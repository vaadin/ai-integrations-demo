package com.vaadin.demo.nordicsupply.data;

/**
 * How a search box becomes a query parameter. Every search method in the repositories takes one {@code :term}:
 * the empty string means "no filter", anything else is a lower-case LIKE pattern that matches the text literally,
 * with the queries' {@code escape '\'} covering a typed {@code %}, {@code _} or {@code \}. The text is lower-cased
 * here and the column through H2's {@code LOWER}, both under the JVM's default locale, which {@code Application} pins
 * so that the two agree on every machine.
 */
public final class SearchTerms {

    private SearchTerms() {}

    /** {@code ""} for no search, otherwise {@code %text%} in lower case with the LIKE wildcards escaped. */
    public static String like(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var literal = text.trim()
                .toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + literal + "%";
    }
}
