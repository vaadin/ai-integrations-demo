package com.vaadin.demo.nordicsupply.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The application's data source comes up with the dataset loaded, every table holding the rows it was generated with,
 * and the application's own set-up scripts run after it.
 */
@SpringBootTest
class DataConfigTest {

    /** The row count of every table the loader fills, as generated on 2026-09-07. */
    private static final Map<String, Integer> ROWS = Map.ofEntries(
            Map.entry("users", 12),
            Map.entry("warehouses", 3),
            Map.entry("categories", 14),
            Map.entry("suppliers", 36),
            Map.entry("products", 3_202),
            Map.entry("price_history", 14_167),
            Map.entry("promotions", 254),
            Map.entry("inventory", 7_700),
            Map.entry("customers", 2_400),
            Map.entry("customer_contacts", 4_320),
            Map.entry("delivery_addresses", 2_922),
            Map.entry("orders", 12_426),
            Map.entry("order_lines", 51_009),
            Map.entry("shipments", 12_961),
            Map.entry("shipment_lines", 49_486),
            Map.entry("delivery_events", 79_015),
            Map.entry("claims", 844),
            Map.entry("claim_lines", 936),
            Map.entry("return_authorisations", 294),
            Map.entry("return_lines", 435),
            Map.entry("credit_notes", 687),
            Map.entry("stock_movements", 77_081));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTableHoldsTheRowsTheDatasetWasGeneratedWith() {
        ROWS.forEach((table, rows) -> assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class))
                .as(table)
                .isEqualTo(rows));
    }

    /**
     * The application's own set-up scripts run after the dataset: the demo users, and a seeded dashboard for each of
     * them, two to five widgets with a question and a reply each (the dataset seeds Test User's first two widgets).
     */
    @Test
    void theSetUpScriptsHaveRun() {
        assertThat(jdbc.queryForList("select user_id from scope_assignment order by user_id", Integer.class))
                .containsExactly(1, 8, 9, 10);
        assertThat(jdbc.queryForList(
                        "select user_id, count(*) as widgets from saved_widgets group by user_id order by user_id"))
                .extracting(row -> row.get("USER_ID"), row -> row.get("WIDGETS"))
                .containsExactly(tuple(1, 4L), tuple(8, 5L), tuple(9, 3L), tuple(10, 2L));
        assertThat(jdbc.queryForObject("select count(*) from saved_widget_messages", Integer.class))
                .isEqualTo(28);
    }

    /**
     * The data scope: one schema of filtered views per region and per country under the base tables' names, each with
     * its account.
     */
    @Test
    void theScopedViewsAndTheirAccountsExist() {
        assertThat(jdbc.queryForList("select table_schema, count(*) as views from information_schema.views"
                        + " where table_schema like 'SCOPED\\_%' group by table_schema order by table_schema"))
                .extracting(row -> row.get("TABLE_SCHEMA"), row -> row.get("VIEWS"))
                .containsExactly(
                        tuple("SCOPED_ALL", 24L),
                        tuple("SCOPED_DE", 24L),
                        tuple("SCOPED_DK", 24L),
                        tuple("SCOPED_DK_DE", 24L),
                        tuple("SCOPED_EE", 24L),
                        tuple("SCOPED_FI", 24L),
                        tuple("SCOPED_FI_EE", 24L),
                        tuple("SCOPED_NO", 24L),
                        tuple("SCOPED_SE", 24L),
                        tuple("SCOPED_SE_NO", 24L));
        assertThat(jdbc.queryForList(
                        "select user_name from information_schema.users where user_name like 'AI_READER%'"
                                + " order by user_name",
                        String.class))
                .containsExactly(
                        "AI_READER_ALL",
                        "AI_READER_DE",
                        "AI_READER_DK",
                        "AI_READER_DK_DE",
                        "AI_READER_EE",
                        "AI_READER_FI",
                        "AI_READER_FI_EE",
                        "AI_READER_NO",
                        "AI_READER_SE",
                        "AI_READER_SE_NO");
    }

    @Test
    void theDatasetsViewsAnswer() {
        assertThat(jdbc.queryForObject("select count(*) from product_current_prices", Integer.class))
                .isEqualTo(3_202);
        assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class))
                .isEqualTo(12);
    }
}
