package com.vaadin.demo.nordicsupply.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/** Row values on screen follow the designs' Nordic format, whatever the machine's locale. */
class FormatsTest {

    @Test
    void amountsAreGroupedWithANoBreakSpaceAndHaveTwoDecimals() {
        assertThat(Formats.money(new BigDecimal("9034.5"))).isEqualTo("9\u00a0034,50");
        assertThat(Formats.money(new BigDecimal("28.2"))).isEqualTo("28,20");
        assertThat(Formats.money(BigDecimal.ZERO)).isEqualTo("0,00");
    }

    @Test
    void aRowValueIsWrittenByItsType() {
        assertThat(Formats.value(null)).isEmpty();
        assertThat(Formats.value(new BigDecimal("4380.4"))).isEqualTo("4\u00a0380,40");
        assertThat(Formats.value(LocalDate.of(2026, 8, 28))).isEqualTo("2026-08-28");
        assertThat(Formats.value(true)).isEqualTo("Yes");
        assertThat(Formats.value(false)).isEqualTo("No");
        assertThat(Formats.value(30)).isEqualTo("30");
        assertThat(Formats.value("PARTIALLY_SHIPPED")).isEqualTo("PARTIALLY_SHIPPED");
    }

    @Test
    void theFormatDoesNotFollowTheDefaultLocale() {
        var previous = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            assertThat(Formats.money(new BigDecimal("9034.5"))).isEqualTo("9\u00a0034,50");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
