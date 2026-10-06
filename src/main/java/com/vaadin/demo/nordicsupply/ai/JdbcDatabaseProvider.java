package com.vaadin.demo.nordicsupply.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.vaadin.flow.component.ai.provider.DatabaseProvider;
import com.vaadin.flow.component.ai.provider.ToolException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.util.Errors;

/**
 * The model's way into the data: the dataset's schema text to read, and queries on the read-only connection of the
 * signed-in user's region, or of the one country the Insights country filter narrows it to. The model receives the
 * schema text, the tool's confirmation and, when the database refuses a query, why; the rows a query returns go to
 * the component that asked for them.
 * <p>
 * Which rows a query returns is decided by the database: the account may read only views that filter by the
 * countries of its scope. The connection is read on every query, so switching the user or the country takes effect
 * on the next question.
 */
public class JdbcDatabaseProvider implements DatabaseProvider {

    /**
     * H2 functions a query may not call, refused by name with one sentence the model can act on in the same turn. The
     * file and link functions need administrator rights, so the database would refuse them anyway and the name only
     * makes the refusal clearer. The account may call the others: the row generators, one call of which can produce
     * millions of rows; {@code SET}, which changes a session variable that a pooled connection would carry into the
     * next question; and the three that answer about a table the account cannot read. None of this is the security
     * control, which is the grants: a name written another way passes the check, and a runaway query is ended by the
     * timeout alone.
     */
    private static final Set<String> DENIED_FUNCTIONS = Set.of(
            "FILE_READ",
            "FILE_WRITE",
            "CSVREAD",
            "CSVWRITE",
            "LINK_SCHEMA",
            "TABLE",
            "TABLE_DISTINCT",
            "SYSTEM_RANGE",
            "SET",
            "DISK_SPACE_USED",
            "DATA_TYPE_SQL",
            "ESTIMATED_ENVELOPE");

    private static final Pattern DENIED_CALL =
            Pattern.compile("\\b(" + String.join("|", DENIED_FUNCTIONS) + ")\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** Quoted text and comments, which the checks must not read as SQL. */
    private static final Pattern QUOTED_OR_COMMENT =
            Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"|--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * A recursive query, of any bound: the check cannot tell a sound bound from a missing one, and a recursive query
     * that counts to a hundred million fills the heap before the timeout or the row cap can act. H2 closes the whole
     * in-memory database when a statement runs out of memory, so the word is refused by name. Like the number check
     * below, this one is partial on purpose, as the others are: it names what a model is likely to write, and the
     * deployment's restart on a closed database is what the demo relies on.
     */
    private static final Pattern RECURSIVE = Pattern.compile("\\bRECURSIVE\\b", Pattern.CASE_INSENSITIVE);

    /**
     * A number of nine digits or more outside quoted text: as the length of a string or the size of a range it is
     * gigabytes. It is not seen when written in quotes, in hexadecimal or as a power or a product, nor when a smaller
     * count meets a long text.
     */
    private static final Pattern HUGE_LITERAL = Pattern.compile("\\b\\d{9,}\\b");

    /** Text and comments alone, leaving quoted names in place for the catalogue check. */
    private static final Pattern TEXT_OR_COMMENT =
            Pattern.compile("'(?:[^']|'')*'|--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * The database's catalogue, which H2 shows to every account: the names of every table in the database, not
     * only the schema the account may read. The schema text is what the model is meant to read instead.
     */
    private static final Pattern CATALOGUE = Pattern.compile("information_schema", Pattern.CASE_INSENSITIVE);

    /** The SQL states of a name the account cannot use: a table or a schema not found, or out of its rights. */
    private static final Set<String> NAME_NOT_AVAILABLE = Set.of("42S02", "90079", "90096");

    /** The first quoted name in the database's message, the one it could not find or open. */
    private static final Pattern QUOTED_NAME = Pattern.compile("\"([^\"]+)\"");

    /** The two statement kinds this connection runs; a query has to start with one of them. */
    private static final String SELECT = "SELECT";

    private static final String WITH = "WITH";

    /** How much of the database's message the model is given back; enough to correct the query, not a whole dump. */
    private static final int ERROR_MESSAGE_LENGTH = 400;

    /**
     * The most rows a query may return. The timeout bounds a query's time, not its memory: H2 builds a whole result
     * before the first row is read, and a missing join condition fills the heap in seconds. So the statement asks the
     * database for one row more than this, and a result that has it is refused with a sentence telling the model to
     * aggregate or narrow the query.
     */
    private static final int MAX_ROWS = 10_000;

    /** The SQL state class of a data exception: a value that could not be converted, or is out of range. */
    private static final String DATA_EXCEPTION = "22";

    /**
     * The SQL states whose message only quotes the SQL the model wrote and the names in it, which is what it needs to
     * correct the query: a syntax error, a column or a function that does not exist, a column missing from the GROUP
     * BY, an ambiguous name, types that cannot be compared, a subquery with more than one row, a column count that
     * does not match, a timeout. Any other state can carry a value from a row: a date that would not parse, an
     * invalid format, a number that came from a value's length.
     */
    private static final Set<String> QUOTES_ONLY_THE_QUERY = Set.of(
            "42000", "42001", "42S21", "42S22", "21S02", "57014", "90012", "90016", "90022", "90053", "90054", "90059",
            "90068", "90110", "HY004");

    private static final String SCHEMA_TEXT = "sql/ai-schema-h2.txt";

    private final transient Supplier<JdbcTemplate> connection;
    private final String schema;

    public JdbcDatabaseProvider(Supplier<JdbcTemplate> connection, PackData pack) {
        this.connection = connection;
        this.schema = schemaText(pack);
    }

    /**
     * The schema text the model reads. Today's date, which the text tells the model to write as a literal instead of
     * the database's own clock, reaches the model through the orchestrator's session context, not through here.
     */
    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String sql) {
        // the checks read the SQL without quoted text, comments and the semicolons H2 accepts at the end
        var code = QUOTED_OR_COMMENT.matcher(sql).replaceAll(" ").strip().replaceAll("[;\\s]+$", "");
        if (!code.regionMatches(true, 0, SELECT, 0, SELECT.length())
                && !code.regionMatches(true, 0, WITH, 0, WITH.length())) {
            throw new ToolException("Only SELECT statements are allowed on this connection.");
        }
        if (code.contains(";")) {
            // H2 would run every statement after the first, on a pooled connection the next question reuses
            throw new ToolException("Send one statement: this connection runs a single SELECT at a time.");
        }
        var denied = DENIED_CALL.matcher(code);
        if (denied.find()) {
            throw new ToolException("The function " + denied.group(1).toUpperCase(Locale.ROOT)
                    + " is not available on this connection.");
        }
        if (RECURSIVE.matcher(code).find()) {
            throw new ToolException("Recursive queries are not available on this connection.");
        }
        if (HUGE_LITERAL.matcher(code).find()) {
            throw new ToolException("A number of nine or more digits is not needed here; use a smaller literal.");
        }
        if (CATALOGUE.matcher(TEXT_OR_COMMENT.matcher(sql).replaceAll(" ")).find()) {
            throw new ToolException(
                    "The catalogue is not available on this connection; the schema text lists the tables and their"
                            + " columns.");
        }
        try {
            // model-written SQL runs here on purpose: the account may read only the filtered views, so anything
            // else fails at the database
            return connection
                    .get()
                    .query(con -> oneRowPastTheCap(con.prepareStatement(sql)), JdbcDatabaseProvider::rowsUpToTheCap);
        } catch (DataAccessException e) {
            // the model can correct the query in the same turn; the log keeps the database's exception
            throw new ToolException("Query failed: " + forTheModel(e), e);
        }
    }

    /** The statement with the database's own row limit, one past the cap, so that the database stops there too. */
    private static PreparedStatement oneRowPastTheCap(PreparedStatement statement) throws SQLException {
        statement.setMaxRows(MAX_ROWS + 1);
        return statement;
    }

    /** The rows as column maps, read up to the cap and no further. */
    private static List<Map<String, Object>> rowsUpToTheCap(ResultSet rs) throws SQLException {
        var mapper = new ColumnMapRowMapper();
        var rows = new ArrayList<Map<String, Object>>();
        while (rs.next()) {
            if (rows.size() == MAX_ROWS) {
                throw new ToolException("The query returns more than " + MAX_ROWS + " rows; aggregate or narrow it.");
            }
            rows.add(mapper.mapRow(rs, rows.size()));
        }
        return rows;
    }

    /**
     * Why the database refused a query, as the model is told. A database that can run no query at all, closed after an
     * out-of-memory or gone from the connection, is said to be unavailable and the model is told to stop: nothing in
     * the query is wrong, and every retry is a paid call. The message of a state that only quotes the SQL and the
     * names in it is the database's own, which says what to correct. For every other state the model gets the state
     * and what to check, since the message can quote a value from a row and the model is never shown rows: a data
     * exception says which value would not convert, and the functions that parse or format a value say which one they
     * could not. A name the account cannot use reads the same whether the table does not exist or exists out of its
     * rights, so that the database's answer confirms nothing about tables the model was not told about.
     */
    private static String forTheModel(DataAccessException e) {
        var sql = sqlException(e);
        var state = sql == null ? null : sql.getSQLState();
        if (e instanceof DataAccessResourceFailureException) {
            // Spring's class for a resource that is gone: H2 closes the whole in-memory database after an
            // out-of-memory, and every pooled connection to it fails from then on
            return "the database is not available right now" + (state == null ? "" : " (SQL state " + state + ")")
                    + "; do not run another query, tell the user to try again later";
        }
        if (state == null) {
            return Errors.rootMessage(e, ERROR_MESSAGE_LENGTH);
        }
        if (NAME_NOT_AVAILABLE.contains(state)) {
            var name = QUOTED_NAME.matcher(String.valueOf(sql.getMessage()));
            return "nothing named " + (name.find() ? name.group(1) : "that")
                    + " is available on this connection; the schema text lists what you can read";
        }
        if (QUOTES_ONLY_THE_QUERY.contains(state)) {
            return Errors.rootMessage(e, ERROR_MESSAGE_LENGTH);
        }
        return state.startsWith(DATA_EXCEPTION)
                ? "a value in the data could not be converted or computed (SQL state " + state
                        + "); check the casts, the literals and the arithmetic"
                : "the database refused the query (SQL state " + state
                        + "); check the functions and the arguments they are given";
    }

    /** The database's own exception in the cause chain, the first that carries a SQL state; null when none does. */
    private static SQLException sqlException(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql;
            }
        }
        return null;
    }

    private static String schemaText(PackData pack) {
        try {
            return pack.read(SCHEMA_TEXT);
        } catch (IOException e) {
            throw new UncheckedIOException("the dataset has no " + SCHEMA_TEXT, e);
        }
    }
}
