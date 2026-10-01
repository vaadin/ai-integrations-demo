package com.vaadin.demo.nordicsupply.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Scopes;

/** What each region's connection can read: its own countries, through its own schema of views and nothing else. */
@SpringBootTest
class ScopedConnectionsTest {

    private static final String COUNTRIES = "SELECT DISTINCT country FROM customers";

    /** The views of tables without customers in them, which every region reads in full. */
    private static final Set<String> CATALOGUE = Set.of(
            "WAREHOUSES",
            "CATEGORIES",
            "SUPPLIERS",
            "PRODUCTS",
            "PRICE_HISTORY",
            "PROMOTIONS",
            "INVENTORY",
            "STOCK_MOVEMENTS",
            "PRODUCT_CURRENT_PRICES",
            "STAFF");

    /** Every other view's rows hang off a row of another: the view, its reference, the view it points at. */
    private static final List<List<String>> OWNERSHIP = List.of(
            List.of("delivery_addresses", "customer_id", "customers"),
            List.of("orders", "customer_id", "customers"),
            List.of("order_lines", "order_id", "orders"),
            List.of("shipments", "order_id", "orders"),
            List.of("shipment_lines", "shipment_id", "shipments"),
            List.of("delivery_events", "shipment_id", "shipments"),
            List.of("claims", "customer_id", "customers"),
            List.of("claim_lines", "claim_id", "claims"),
            List.of("return_authorisations", "customer_id", "customers"),
            List.of("return_lines", "return_id", "return_authorisations"),
            List.of("credit_notes", "customer_id", "customers"),
            List.of("late_shipments", "customer_id", "customers"),
            List.of("open_claims", "customer_id", "customers"));

    @Autowired
    private ScopedConnections connections;

    @Autowired
    private PackData pack;

    @Autowired
    private DataSource dataSource;

    /** The aliases a model writes for a chart's columns are names on every region's connection, not keywords. */
    @Test
    void aModelsAliasesThatH2ReadsAsKeywordsAreNames() {
        for (var scope : Scopes.roleScopes()) {
            assertThat(connections
                            .templateFor(scope)
                            .queryForMap("SELECT 1 AS value, 2026 AS year, 9 AS month, 7 AS day, 10 AS hour,"
                                    + " 30 AS minute, 15 AS second, 'k' AS key"))
                    .as(scope.key())
                    .containsKeys("VALUE", "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "KEY");
        }
    }

    /** The countries the lists filter by are the ones the database scopes by: the two lists cannot drift apart. */
    @Test
    void aRegionSeesTheCountriesItsScopeNames() {
        for (var scope : Scopes.roleScopes()) {
            assertThat(connections.templateFor(scope).queryForList(COUNTRIES, String.class))
                    .as(scope.key())
                    .containsExactlyInAnyOrderElementsOf(scope.countries());
        }
    }

    /** Every customer's rows belong to exactly one of the three regions, and the catalogue to all of them. */
    @Test
    void theRegionsSplitEveryViewWithCustomersAndShareTheCatalogue() {
        var admin = new JdbcTemplate(dataSource);
        var views = admin.queryForList(
                "SELECT table_name FROM information_schema.views WHERE table_schema = ?",
                String.class,
                ScopedConnections.schemaFor(Scopes.FI_EE).toUpperCase());
        // the catalogue, the customers, and every view whose rows hang off them
        assertThat(views).hasSize(CATALOGUE.size() + 1 + OWNERSHIP.size());
        for (var view : views) {
            var all = admin.queryForObject("SELECT COUNT(*) FROM public." + view, Long.class);
            var regions = new ArrayList<Long>();
            for (var scope : List.of(Scopes.FI_EE, Scopes.SE_NO, Scopes.DK_DE)) {
                regions.add(count(connections.templateFor(scope), view));
            }
            assertThat(count(connections.templateFor(Scopes.ALL), view))
                    .as(view)
                    .isEqualTo(all);
            if (CATALOGUE.contains(view)) {
                assertThat(regions).as(view).containsOnly(all);
            } else {
                assertThat(regions.stream().mapToLong(Long::longValue).sum())
                        .as(view)
                        .isEqualTo(all);
                assertThat(regions).as(view).allMatch(n -> n < all);
            }
        }
    }

    /** Whatever a region sees hangs off a customer it sees. */
    @Test
    void everyRowARegionSeesHangsOffACustomerItSees() {
        for (var scope : Scopes.roleScopes()) {
            assertThat(orphans(scope)).as(scope.key()).isEmpty();
        }
    }

    /**
     * A subquery over a view is re-run for every outer row when H2 cannot cache it, which is why the views carry
     * their countries as literals: the shapes a model writes readily answer in well under a second.
     */
    @Test
    void aSubqueryOverAViewAnswersQuickly() {
        var lars = connections.templateFor(Scopes.FI_EE);
        lars.setQueryTimeout(5);

        for (var shape : List.of(
                "SELECT COUNT(*) FROM customers WHERE id NOT IN (SELECT customer_id FROM orders)",
                "SELECT status, COUNT(*) FROM orders WHERE customer_id IN"
                        + " (SELECT id FROM customers WHERE city = 'Tallinn') GROUP BY status",
                "SELECT COUNT(*) FROM order_lines WHERE order_id IN (SELECT id FROM orders WHERE status = 'SHIPPED')",
                "SELECT movement_type, COUNT(*) FROM stock_movements sm WHERE EXISTS"
                        + " (SELECT 1 FROM shipments s WHERE s.shipment_number = sm.reference) GROUP BY movement_type")) {
            assertThat(lars.queryForList(shape)).as(shape).isNotEmpty();
        }
    }

    /** The model reads what the lists show: as Lars, 805 customers and 4 191 orders. */
    @Test
    void theModelSeesTheRowsTheListsShow() {
        var provider = new JdbcDatabaseProvider(() -> connections.templateFor(Scopes.FI_EE), pack);

        assertThat(provider.executeQuery("SELECT COUNT(*) AS n FROM customers"))
                .singleElement()
                .satisfies(row -> assertThat(row.get("N")).isEqualTo(805L));
        assertThat(provider.executeQuery("SELECT COUNT(*) AS n FROM orders"))
                .singleElement()
                .satisfies(row -> assertThat(row.get("N")).isEqualTo(4_191L));
    }

    @Test
    void noAccountCanReadATableOfThePublicSchema() {
        var tables = new JdbcTemplate(dataSource)
                .queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'", String.class);
        assertThat(tables).contains("CUSTOMERS", "SCOPE_ASSIGNMENT", "ACTIVITY_LOG");
        for (var scope : Scopes.roleScopes()) {
            for (var table : tables) {
                assertThatThrownBy(() -> connections
                                .templateFor(scope)
                                .queryForObject("SELECT COUNT(*) FROM public." + table, Long.class))
                        .as(scope.key() + " " + table)
                        .hasMessageContaining("Not enough rights for object \"PUBLIC." + table + "\"");
            }
        }
    }

    @Test
    void anotherRegionsSchemaIsOutOfReach() {
        assertThatThrownBy(() -> connections
                        .templateFor(Scopes.FI_EE)
                        .queryForObject("SELECT COUNT(*) FROM scoped_se_no.customers", Long.class))
                .hasMessageContaining("Not enough rights for object \"SCOPED_SE_NO.CUSTOMERS\"");
    }

    /** A table out of the account's rights and one that does not exist read alike, so neither confirms a name. */
    @Test
    void aForbiddenAndAMissingNameReadAlikeToTheModel() {
        var provider = new JdbcDatabaseProvider(() -> connections.templateFor(Scopes.FI_EE), pack);

        assertThatThrownBy(() -> provider.executeQuery("SELECT COUNT(*) FROM public.users"))
                .hasMessage("Query failed: nothing named PUBLIC.USERS is available on this connection;"
                        + " the schema text lists what you can read");
        assertThatThrownBy(() -> provider.executeQuery("SELECT COUNT(*) FROM public.nothing"))
                .hasMessage("Query failed: nothing named NOTHING is available on this connection;"
                        + " the schema text lists what you can read");
        assertThatThrownBy(() -> provider.executeQuery("SELECT COUNT(*) FROM elsewhere.customers"))
                .hasMessage("Query failed: nothing named ELSEWHERE is available on this connection;"
                        + " the schema text lists what you can read");
    }

    @Test
    void onlyARegionHasAConnection() {
        assertThatThrownBy(() -> connections.templateFor(Scope.of("fi", "Finland", "FI")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a region: fi");
    }

    private static long count(JdbcTemplate template, String view) {
        return template.queryForObject("SELECT COUNT(*) FROM " + view, Long.class);
    }

    /** The views with rows whose owner the region does not see. */
    private List<String> orphans(Scope scope) {
        return connections.templateFor(scope).execute((ConnectionCallback<List<String>>) con -> {
            try (var statement = con.createStatement()) {
                var found = new ArrayList<String>();
                for (var link : OWNERSHIP) {
                    try (var rs = statement.executeQuery("SELECT COUNT(*) FROM " + link.get(0) + " x WHERE NOT EXISTS"
                            + " (SELECT 1 FROM " + link.get(2) + " p WHERE p.id = x." + link.get(1) + ")")) {
                        rs.next();
                        if (rs.getLong(1) > 0) {
                            found.add(link.get(0));
                        }
                    }
                }
                return found;
            }
        });
    }
}
