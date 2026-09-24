package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** The customer queries against the dataset as generated. */
@SpringBootTest
class CustomerRepositoryTest {

    private static final PageRequest ALL = PageRequest.of(0, 3_000);

    @Autowired
    private CustomerRepository customers;

    @Test
    void countsTheActiveCustomers() {
        assertThat(customers.countByActiveTrue()).isEqualTo(2_283);
    }

    @Test
    void anEmptyTermMatchesEveryCustomer() {
        assertThat(customers.search("", PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(2_400);
    }

    @Test
    void aTermMatchesTheName() {
        var rows = customers.search(SearchTerms.like("retki"), ALL).getContent();

        assertThat(rows).hasSize(123).allSatisfy(row -> assertThat(row.name()).containsIgnoringCase("retki"));
    }

    @Test
    void aTermMatchesTheCustomerNumber() {
        var rows = customers.search(SearchTerms.like("C-10001"), ALL).getContent();

        assertThat(rows)
                .singleElement()
                .satisfies(row -> assertThat(row.customerNumber()).isEqualTo("C-10001"));
    }

    @Test
    void aTermMatchesTheCity() {
        var rows = customers.search(SearchTerms.like("Umeå"), ALL).getContent();

        assertThat(rows).hasSize(40).allSatisfy(row -> assertThat(row.city()).isEqualTo("Umeå"));
    }
}
