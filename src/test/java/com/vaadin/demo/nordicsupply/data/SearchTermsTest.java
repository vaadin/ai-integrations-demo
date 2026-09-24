package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A search box's text becomes the one {@code :term} the repositories' search queries take. */
class SearchTermsTest {

    @Test
    void nothingTypedMeansNoFilter() {
        assertThat(SearchTerms.like(null)).isEmpty();
        assertThat(SearchTerms.like("")).isEmpty();
        assertThat(SearchTerms.like("   ")).isEmpty();
    }

    @Test
    void textBecomesALowerCaseContainsPattern() {
        assertThat(SearchTerms.like(" Umeå ")).isEqualTo("%umeå%");
        assertThat(SearchTerms.like("SO-2026")).isEqualTo("%so-2026%");
    }
}
