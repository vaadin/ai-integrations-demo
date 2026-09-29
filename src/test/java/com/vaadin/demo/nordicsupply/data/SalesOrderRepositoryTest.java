package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.vaadin.demo.nordicsupply.session.Scopes;

/** The order queries against the dataset as generated, whose as-of date is 2026-09-07, within a region's countries. */
@SpringBootTest
class SalesOrderRepositoryTest {

    private static final PageRequest ALL = PageRequest.of(0, 13_000);

    @Autowired
    private SalesOrderRepository orders;

    @Test
    void anEmptyTermMatchesEveryOrder() {
        assertThat(orders.search("", Scopes.ALL.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(12_426);
    }

    @Test
    void aTermMatchesTheOrderNumber() {
        var rows = orders.search(SearchTerms.like("SO-2026-0073"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows)
                .hasSize(71)
                .allSatisfy(row -> assertThat(row.orderNumber()).startsWith("SO-2026-0073"));
    }

    @Test
    void aTermMatchesTheCustomer() {
        var rows = orders.search(SearchTerms.like("arcticstore"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows)
                .hasSize(96)
                .allSatisfy(row -> assertThat(row.customer()).startsWith("Arcticstore"));
    }

    @Test
    void aTermMatchesTheStatus() {
        var rows = orders.search(SearchTerms.like("partially_shipped"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row.status()).isEqualTo("PARTIALLY_SHIPPED"));
    }

    @Test
    void aWildcardInTheTermMatchesOnlyItself() {
        var underscore = orders.search(SearchTerms.like("_"), Scopes.ALL.countries(), ALL)
                .getContent();
        var percent = orders.search(SearchTerms.like("%"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(underscore)
                .hasSize(142)
                .allSatisfy(row -> assertThat(row.status()).isEqualTo("PARTIALLY_SHIPPED"));
        assertThat(percent).isEmpty();
    }

    @Test
    void aRegionSeesOnlyTheOrdersOfItsOwnCustomers() {
        assertThat(orders.search("", Scopes.FI_EE.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(4_191);
        assertThat(orders.search("", Scopes.SE_NO.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(5_868);
        assertThat(orders.search("", Scopes.DK_DE.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(2_367);
    }

    /** Of the 96 orders of the Arcticstore customers, the 42 of the Finnish and Estonian ones. */
    @Test
    void aSearchFindsOnlyTheRegionsOrders() {
        var rows = orders.search(SearchTerms.like("arcticstore"), Scopes.FI_EE.countries(), ALL)
                .getContent();

        assertThat(rows)
                .hasSize(42)
                .allSatisfy(row -> assertThat(row.customer()).startsWith("Arcticstore"));
    }
}
