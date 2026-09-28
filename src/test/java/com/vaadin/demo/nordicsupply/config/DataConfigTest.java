package com.vaadin.demo.nordicsupply.config;

import static org.assertj.core.api.Assertions.assertThat;

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
            Map.entry("stock_movements", 77_081),
            Map.entry("saved_widgets", 2));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTableHoldsTheRowsTheDatasetWasGeneratedWith() {
        ROWS.forEach((table, rows) -> assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class))
                .as(table)
                .isEqualTo(rows));
    }

    /** The application's own set-up scripts run after the dataset. */
    @Test
    void theSetUpScriptsHaveRun() {
        assertThat(jdbc.queryForList("select user_id from scope_assignment order by user_id", Integer.class))
                .containsExactly(1, 8, 9, 10);
    }

    @Test
    void theDatasetsViewsAnswer() {
        assertThat(jdbc.queryForObject("select count(*) from product_current_prices", Integer.class))
                .isEqualTo(3_202);
        assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class))
                .isEqualTo(12);
    }
}
