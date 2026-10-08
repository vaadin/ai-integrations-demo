package com.vaadin.demo.nordicsupply.session;

import java.io.Serializable;
import java.util.List;

/**
 * The part of the data a user sees: a region a demo user is responsible for, or one of its countries alone, as the
 * Dashboard country filter narrows it. Its key, as the database names its schema; its label for the screen; and its
 * countries, the values of the customers' {@code country} column, so that a scope can be compared with the data
 * directly. A scope covers the customers of these countries and everything that hangs off them; the catalogue is the
 * same for everyone.
 */
public record Scope(String key, String label, List<String> countries) implements Serializable {

    /** A scope over the given countries, in the order they are listed. */
    public static Scope of(String key, String label, String... countries) {
        return new Scope(key, label, List.of(countries));
    }
}
