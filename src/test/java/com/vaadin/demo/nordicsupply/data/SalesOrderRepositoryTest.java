package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** The order queries against the dataset as generated, whose as-of date is 2026-09-07. */
@SpringBootTest
class SalesOrderRepositoryTest {

    private static final PageRequest ALL = PageRequest.of(0, 13_000);

    @Autowired
    private SalesOrderRepository orders;

    @Test
    void countsTheOrdersPlacedSinceADay() {
        assertThat(orders.countByPlacedAtGreaterThanEqual(
                        LocalDate.of(2026, 9, 1).atStartOfDay()))
                .isEqualTo(55);
    }

    @Test
    void anEmptyTermMatchesEveryOrder() {
        assertThat(orders.search("", PageRequest.of(0, 1)).getTotalElements()).isEqualTo(12_426);
    }

    @Test
    void aTermMatchesTheOrderNumber() {
        var rows = orders.search(SearchTerms.like("SO-2026-0073"), ALL).getContent();

        assertThat(rows)
                .hasSize(71)
                .allSatisfy(row -> assertThat(row.orderNumber()).startsWith("SO-2026-0073"));
    }

    @Test
    void aTermMatchesTheCustomer() {
        var rows = orders.search(SearchTerms.like("arcticstore"), ALL).getContent();

        assertThat(rows)
                .hasSize(96)
                .allSatisfy(row -> assertThat(row.customer()).startsWith("Arcticstore"));
    }

    @Test
    void aTermMatchesTheStatus() {
        var rows = orders.search(SearchTerms.like("partially_shipped"), ALL).getContent();

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row.status()).isEqualTo("PARTIALLY_SHIPPED"));
    }
}
