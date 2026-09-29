package com.vaadin.demo.nordicsupply.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

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
        var count = "WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM t WHERE n < %d) SELECT n FROM t";

        assertThatThrownBy(() -> provider.executeQuery(count.formatted(10_001)))
                .isInstanceOf(ToolException.class)
                .hasMessage("The query returns more than 10000 rows; aggregate or narrow it.");
        assertThat(provider.executeQuery(count.formatted(10_000))).hasSize(10_000);
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
