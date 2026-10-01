package com.vaadin.demo.nordicsupply.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.vaadin.flow.component.ai.provider.ToolException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.vaadin.demo.nordicsupply.config.PackData;

/**
 * The provider runs what the model writes on the connection it is given, reads back, and turns anything else into
 * a sentence the model can act on.
 */
class JdbcDatabaseProviderTest {

    private static EmbeddedDatabase db;
    private static JdbcTemplate jdbc;
    private static JdbcDatabaseProvider provider;

    @BeforeAll
    static void aSmallDatabaseOfItsOwn() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("providertest")
                .build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE widgets (id INT PRIMARY KEY, name VARCHAR(20))");
        jdbc.update("INSERT INTO widgets (id, name) VALUES (1, 'one'), (2, 'two')");
        jdbc.execute("CREATE TABLE numbers AS SELECT x FROM SYSTEM_RANGE(1, 1000)");
        jdbc.execute("CREATE TABLE people (name VARCHAR(40))");
        jdbc.update("INSERT INTO people (name) VALUES ('Kairagear Oy')");
        provider = new JdbcDatabaseProvider(() -> jdbc, new PackData());
    }

    @AfterAll
    static void closed() {
        db.shutdown();
    }

    @Test
    void theSchemaIsTheDatasetsText() throws Exception {
        assertThat(provider.getSchema())
                .startsWith("You are querying the order-desk database of Nordic Supply")
                .isEqualTo(new PackData().read("sql/ai-schema-h2.txt"));
    }

    /** A result beyond the cap is refused, and the model is told what to do instead. */
    @Test
    void aResultBeyondTheCapIsRefusedAndOneAtTheCapReturned() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT a.x FROM numbers a, numbers b"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The query returns more than 10000 rows; aggregate or narrow it.");
        assertThat(provider.executeQuery("SELECT a.x FROM numbers a, numbers b LIMIT 10000"))
                .hasSize(10_000);
    }

    /**
     * The two shapes that fill the heap before the timeout or the cap can act are refused by name, however the word is
     * cased and from nine digits up: H2 would close the whole database on the out-of-memory they cause. A number of
     * eight digits, and either shape inside a text, is a query like any other.
     */
    @Test
    void aRecursiveQueryAndAHugeLiteralAreRefusedBeforeTheDatabase() {
        for (var query : List.of(
                "WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM t WHERE n < 100000000)"
                        + " SELECT MAX(n) FROM t",
                "with recursive t(n) as (select 1 union all select n + 1 from t where n < 10) select max(n) from t")) {
            assertThatThrownBy(() -> provider.executeQuery(query))
                    .as(query)
                    .isInstanceOf(ToolException.class)
                    .hasMessage("Recursive queries are not available on this connection.");
        }
        for (var digits : List.of("2147483647", "100000000")) {
            assertThatThrownBy(() -> provider.executeQuery(
                            "SELECT LPAD(name, " + digits + ", 'x') FROM widgets WHERE name = 'one'"))
                    .as(digits)
                    .isInstanceOf(ToolException.class)
                    .hasMessage("A number of nine or more digits is not needed here; use a smaller literal.");
        }
        assertThat(provider.executeQuery("SELECT LPAD(name, 10000000, 'x') FROM widgets WHERE name = 'one'"))
                .hasSize(1);
        assertThat(provider.executeQuery("SELECT name FROM widgets WHERE name <> 'recursive 1234567890'"))
                .hasSize(2);
    }

    /**
     * The database stops at the cap too, instead of building a whole result first: a billion rows would take far
     * longer than this connection's one-second timeout, and the model hears about the cap, not the timeout.
     */
    @Test
    void theDatabaseStopsAtTheCapInsteadOfBuildingTheWholeResult() {
        var impatient = new JdbcTemplate(db);
        impatient.setQueryTimeout(1);
        var capped = new JdbcDatabaseProvider(() -> impatient, new PackData());

        assertThatThrownBy(() -> capped.executeQuery("SELECT a.x, b.x, c.x FROM numbers a, numbers b, numbers c"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The query returns more than 10000 rows; aggregate or narrow it.");
    }

    /** The database's clock is the machine's, so the only word about it is not to use it. */
    @Test
    void theSchemaNeverSendsTheModelToTheDatabasesClock() {
        assertThat(provider.getSchema().lines().filter(line -> line.contains("CURRENT_")))
                .isNotEmpty()
                .allMatch(line -> line.contains("Never CURRENT_DATE or CURRENT_TIMESTAMP"));
    }

    @Test
    void aSelectReturnsItsRowsAsColumnMaps() {
        assertThat(provider.executeQuery("SELECT id, name FROM widgets ORDER BY id"))
                .containsExactly(Map.of("ID", 1, "NAME", "one"), Map.of("ID", 2, "NAME", "two"));
    }

    @Test
    void aCommonTableExpressionAndTrailingSemicolonsAreFine() {
        assertThat(provider.executeQuery("WITH t AS (SELECT 1 AS n) SELECT n FROM t"))
                .hasSize(1);
        assertThat(provider.executeQuery("  select name from widgets;; ")).hasSize(2);
    }

    @Test
    void anythingButASelectIsRefusedBeforeItReachesTheDatabase() {
        assertThatThrownBy(() -> provider.executeQuery("DELETE FROM widgets"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Only SELECT statements are allowed on this connection.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM widgets", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void aFileFunctionIsRefusedByName() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT * FROM CSVREAD('x.csv')"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The function CSVREAD is not available on this connection.");
        assertThatThrownBy(() -> provider.executeQuery("SELECT file_read ('x') FROM widgets"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The function FILE_READ is not available on this connection.");
    }

    /** H2 shows every account the whole catalogue, so the model is sent back to the schema text instead. */
    @Test
    void theCatalogueIsRefusedByNameHoweverItIsWritten() {
        for (var query : List.of(
                "SELECT table_name FROM information_schema.tables",
                "SELECT table_name FROM \"INFORMATION_SCHEMA\".\"TABLES\"",
                "SELECT * FROM Information_Schema.columns /* the catalogue */")) {
            assertThatThrownBy(() -> provider.executeQuery(query))
                    .as(query)
                    .isInstanceOf(ToolException.class)
                    .hasMessage("The catalogue is not available on this connection; the schema text lists the tables"
                            + " and their columns.");
        }
        assertThat(provider.executeQuery("SELECT name FROM widgets WHERE name <> 'information_schema'"))
                .hasSize(2);
    }

    /** The functions that answer about a table the account cannot read are refused with the file functions. */
    @Test
    void aFunctionThatDescribesATableIsRefusedByName() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT DISK_SPACE_USED('PUBLIC.USERS')"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The function DISK_SPACE_USED is not available on this connection.");
    }

    /** A name the database does not know reads as unavailable, in the same words as one out of the rights. */
    @Test
    void aMissingTableReadsAsUnavailable() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT * FROM gadgets"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Query failed: nothing named GADGETS is available on this connection;"
                        + " the schema text lists what you can read");
    }

    /** A session variable set by one question would stay on the pooled connection for the next. */
    @Test
    void settingASessionVariableIsRefusedByName() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT SET(@country, 'EE') AS v"))
                .isInstanceOf(ToolException.class)
                .hasMessage("The function SET is not available on this connection.");
    }

    /** H2 runs every statement it is given, so a second one is refused before the database sees the first. */
    @Test
    void aSecondStatementIsRefusedBeforeItReachesTheDatabase() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT name FROM widgets; DELETE FROM widgets"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Send one statement: this connection runs a single SELECT at a time.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM widgets", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void quotedTextAndCommentsAreNotReadAsSql() {
        assertThat(provider.executeQuery("-- the widgets\nSELECT name AS \"Table (name)\" FROM widgets"
                        + " WHERE name <> 'a; SET(x)' /* ; */"))
                .hasSize(2);
    }

    @Test
    void aQueryTheDatabaseRejectsComesBackAsItsMessageForTheModel() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT nothing FROM widgets"))
                .isInstanceOf(ToolException.class)
                .hasMessageStartingWith("Query failed: Column \"NOTHING\" not found")
                .hasCauseInstanceOf(DataAccessException.class);
    }

    /** The model is given enough of the database's message to correct the query, not all of it. */
    @Test
    void aLongDatabaseMessageIsClipped() {
        var query = "SELECT nothing FROM widgets WHERE name = '" + "x".repeat(500) + "'";

        assertThatThrownBy(() -> provider.executeQuery(query))
                .isInstanceOf(ToolException.class)
                .hasMessageStartingWith("Query failed: Column \"NOTHING\" not found")
                .hasMessageEndingWith("…")
                .satisfies(e -> assertThat(e.getMessage()).hasSize("Query failed: ".length() + 400 + 1));
    }

    /** The model never sees rows, so a value that failed a conversion is not quoted back to it. */
    @Test
    void aValueFromARowNeverReachesTheModel() {
        assertThatThrownBy(() -> provider.executeQuery("SELECT CAST(name AS INT) FROM widgets"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Query failed: a value in the data could not be converted or computed (SQL state 22018);"
                        + " check the casts, the literals and the arithmetic");
    }

    /**
     * The functions that parse, decode or format a value fail with the value in their message, under a state that is
     * not a data exception. None of it reaches the model.
     */
    @Test
    void noFailureQuotesAValueFromARowOrItsLengthBackToTheModel() {
        for (var expression : List.of(
                "PARSEDATETIME(name, 'yyyy')",
                "STRINGDECODE(name || '\\z')",
                "TO_CHAR(CURRENT_TIMESTAMP, name)",
                "FORMATDATETIME(CURRENT_TIMESTAMP, name)",
                "RANDOM_UUID(LENGTH(name))")) {
            assertThatThrownBy(() -> provider.executeQuery("SELECT " + expression + " FROM people"))
                    .as(expression)
                    .isInstanceOf(ToolException.class)
                    .hasMessageMatching("Query failed: the database refused the query \\(SQL state \\w{5}\\);"
                            + " check the functions and the arguments they are given");
        }
    }

    /**
     * What a model gets wrong in a query is still told in the database's words, since those say what to correct: a
     * syntax error, a missing or ambiguous name, a column missing from the GROUP BY, an aggregate where none may be,
     * types that cannot be compared, a subquery with more than one row, a column count that does not match.
     */
    @Test
    void theMistakesAModelMakesComeBackAsTheDatabasesMessage() {
        var mistakes = new LinkedHashMap<String, String>();
        mistakes.put("SELECT name, COUNT(*) FROM widgets", "must be in the GROUP BY list");
        mistakes.put("SELECT NOPE(1)", "Function \"NOPE\" not found");
        mistakes.put("SELECT name FORM widgets", "Syntax error in SQL statement");
        mistakes.put("SELECT 1 AS select", "Syntax error in SQL statement");
        mistakes.put("SELECT id FROM widgets a, widgets b", "Ambiguous column name \"ID\"");
        mistakes.put("SELECT id FROM widgets UNION SELECT id, name FROM widgets", "Column count does not match");
        mistakes.put("SELECT * FROM (SELECT 1, 2) AS t(a, a)", "Duplicate column name \"A\"");
        mistakes.put("SELECT name FROM widgets WHERE id = ?", "Parameter \"#1\" is not set");
        mistakes.put(
                "SELECT (SELECT name FROM widgets) FROM (VALUES (1)) v", "Scalar subquery contains more than one row");
        mistakes.put("SELECT * FROM widgets WHERE COUNT(*) > 1", "Invalid use of aggregate function \"COUNT(*)\"");
        mistakes.put("SELECT name FROM widgets ORDER BY 3", "Order by expression \"3\" must be in the result list");
        mistakes.put("SELECT * FROM widgets WHERE id = CURRENT_DATE", "Values of types \"INTEGER\" and \"DATE\"");
        mistakes.put("SELECT CAST(name AS nope) FROM widgets", "Unknown data type: \"NOPE\"");

        mistakes.forEach((query, said) -> assertThatThrownBy(() -> provider.executeQuery(query))
                .as(query)
                .isInstanceOf(ToolException.class)
                .hasMessageStartingWith("Query failed: ")
                .hasMessageContaining(said));
    }

    /** A query the timeout ends is told in the database's words too, so that the model narrows it. */
    @Test
    void aQueryTheTimeoutEndsComesBackAsTheDatabasesMessage() {
        var impatient = new JdbcTemplate(db);
        impatient.setQueryTimeout(1);
        var timed = new JdbcDatabaseProvider(() -> impatient, new PackData());

        assertThatThrownBy(() -> timed.executeQuery("SELECT COUNT(*) FROM numbers a, numbers b, numbers c, numbers d"))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("Statement was canceled or the session timed out");
    }

    /**
     * H2 reports an unexpected Java exception as a general error with the exception's own message; for a text repeated
     * past the size of an array that is a number that came from the text's length. The state is not one that quotes
     * only the query, so the model is not given the message.
     */
    @Test
    void aGeneralErrorComesBackWithoutTheDatabasesMessage() throws SQLException {
        var overflowing = failingWith(new SQLException("-18", "HY000", 50000));
        var failing = new JdbcDatabaseProvider(() -> new JdbcTemplate(overflowing), new PackData());

        assertThatThrownBy(() -> failing.executeQuery("SELECT 1"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Query failed: the database refused the query (SQL state HY000);"
                        + " check the functions and the arguments they are given");
    }

    /**
     * A database that can run no query at all, closed after an out-of-memory or gone from the connection, is not the
     * query's fault: the model is told to stop, since every retry is a paid call, and so is a connection that cannot be
     * opened.
     */
    @Test
    void aDatabaseThatCanRunNoQueryTellsTheModelToStop() throws SQLException {
        var shutDown =
                failingWith(new SQLNonTransientConnectionException("The database has been closed", "90098", 90098));
        var closed = new JdbcDatabaseProvider(() -> new JdbcTemplate(shutDown), new PackData());
        var unreachable = mock(DataSource.class);
        when(unreachable.getConnection()).thenThrow(new SQLException("Connection refused", "08001"));
        var gone = new JdbcDatabaseProvider(() -> new JdbcTemplate(unreachable), new PackData());

        assertThatThrownBy(() -> closed.executeQuery("SELECT 1"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Query failed: the database is not available right now (SQL state 90098); do not run"
                        + " another query, tell the user to try again later");
        assertThatThrownBy(() -> gone.executeQuery("SELECT 1"))
                .isInstanceOf(ToolException.class)
                .hasMessage("Query failed: the database is not available right now (SQL state 08001); do not run"
                        + " another query, tell the user to try again later");
    }

    /** A data source whose H2 connection fails every statement with the given exception. */
    private static DataSource failingWith(SQLException failure) throws SQLException {
        var metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        var connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.prepareStatement(anyString())).thenThrow(failure);
        var source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);
        return source;
    }

    /** A failure that is not the query's is not handed to the model as advice: the tool reports it as an error. */
    @Test
    void aFailureOutsideTheQueryIsNotTheModelsToCorrect() {
        var broken = new JdbcDatabaseProvider(
                () -> {
                    throw new IllegalStateException("no connection");
                },
                new PackData());

        assertThatThrownBy(() -> broken.executeQuery("SELECT 1"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ToolException.class);
    }

    @Test
    void theConnectionIsTakenAfreshForEveryQuery() {
        var current = new JdbcTemplate[] {jdbc};
        var switching = new JdbcDatabaseProvider(() -> current[0], new PackData());
        assertThat(switching.executeQuery("SELECT name FROM widgets")).hasSize(2);

        var other = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("providertest-other")
                .build();
        try {
            current[0] = new JdbcTemplate(other);
            current[0].execute("CREATE TABLE widgets (id INT PRIMARY KEY)");

            assertThat(switching.executeQuery("SELECT * FROM widgets")).isEmpty();
        } finally {
            other.shutdown();
        }
    }
}
