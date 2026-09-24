package com.vaadin.demo.nordicsupply;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** What the application settles for every machine it runs on. */
@SpringBootTest
class ApplicationTest {

    @Test
    void theJvmLocaleIsPinnedSoThatSearchBehavesTheSameEverywhere() {
        assertThat(Locale.getDefault()).isEqualTo(Locale.ROOT);
    }
}
