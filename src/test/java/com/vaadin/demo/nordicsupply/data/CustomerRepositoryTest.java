package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.vaadin.demo.nordicsupply.session.Scopes;

/** The customer queries against the dataset as generated, within a region's countries. */
@SpringBootTest
class CustomerRepositoryTest {

    private static final PageRequest ALL = PageRequest.of(0, 3_000);

    @Autowired
    private CustomerRepository customers;

    @Test
    void anEmptyTermMatchesEveryCustomer() {
        assertThat(customers
                        .search("", Scopes.ALL.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(2_400);
    }

    @Test
    void aTermMatchesTheName() {
        var rows = customers
                .search(SearchTerms.like("retki"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows).hasSize(123).allSatisfy(row -> assertThat(row.name()).containsIgnoringCase("retki"));
    }

    @Test
    void aTermMatchesTheCustomerNumber() {
        var rows = customers
                .search(SearchTerms.like("C-10001"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows)
                .singleElement()
                .satisfies(row -> assertThat(row.customerNumber()).isEqualTo("C-10001"));
    }

    @Test
    void aTermMatchesTheCity() {
        var rows = customers
                .search(SearchTerms.like("Umeå"), Scopes.ALL.countries(), ALL)
                .getContent();

        assertThat(rows).hasSize(40).allSatisfy(row -> assertThat(row.city()).isEqualTo("Umeå"));
    }

    @Test
    void aRegionSeesOnlyItsOwnCustomers() {
        var finlandAndEstonia =
                customers.search("", Scopes.FI_EE.countries(), ALL).getContent();

        assertThat(finlandAndEstonia)
                .hasSize(805)
                .allSatisfy(row -> assertThat(row.country()).isIn("FI", "EE"));
    }

    /** Counted as the list counts its rows, by the query for one row. */
    @Test
    void eachRegionCountsOnlyItsOwnCustomers() {
        assertThat(customers
                        .search("", Scopes.FI_EE.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(805);
        assertThat(customers
                        .search("", Scopes.SE_NO.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(1_163);
        assertThat(customers
                        .search("", Scopes.DK_DE.countries(), PageRequest.of(0, 1))
                        .getTotalElements())
                .isEqualTo(432);
    }

    @Test
    void aCustomerOutsideTheRegionIsInvisibleEvenWhenSearchedFor() {
        assertThat(customers
                        .search(SearchTerms.like("Umeå"), Scopes.FI_EE.countries(), ALL)
                        .getContent())
                .isEmpty();
    }
}
