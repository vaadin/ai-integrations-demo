package com.vaadin.demo.nordicsupply.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;

import com.vaadin.demo.nordicsupply.config.DisplayLocale;

/**
 * How the values of a row are written on screen. One place, in the application's one locale, {@link DisplayLocale}:
 * amounts read {@code 9 034,50}, never {@code 9,034.50}; dates stay ISO, {@code 2026-08-28}.
 */
public final class Formats {

    private Formats() {}

    /** An amount: grouped, always two decimals. The currency is named by the column, not repeated per row. */
    public static String money(BigDecimal amount) {
        var money = NumberFormat.getNumberInstance(DisplayLocale.LOCALE);
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
        return money.format(amount);
    }

    /** A value straight out of a result row, as the lists show it. */
    public static String value(Object v) {
        return switch (v) {
            case null -> "";
            case BigDecimal b -> money(b);
            case LocalDate d -> d.toString();
            case Boolean b -> b ? "Yes" : "No";
            default -> String.valueOf(v);
        };
    }
}
