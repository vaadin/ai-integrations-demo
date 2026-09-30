package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The contact queries against the dataset as generated. */
@SpringBootTest
class CustomerContactRepositoryTest {

    @Autowired
    private CustomerContactRepository contacts;

    @Test
    void aCustomersContactsAreItsOwnThePrimaryOneFirst() {
        assertThat(contacts.optionsOf(1))
                .extracting(
                        CustomerContactRepository.Option::name,
                        CustomerContactRepository.Option::role,
                        CustomerContactRepository.Option::email)
                .containsExactly(
                        tuple("Elina Järvinen", "Store Manager", "elina.jarvinen@retkivakka.example"),
                        tuple("Sanna Kallio", "Assistant Manager", "sanna.kallio@retkivakka.example"));
    }
}
