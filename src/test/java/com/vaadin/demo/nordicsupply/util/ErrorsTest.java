package com.vaadin.demo.nordicsupply.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The message shown for a failure is the innermost cause's, clipped to a readable length. */
class ErrorsTest {

    @Test
    void theRootCausesMessageIsTheOneShown() {
        var failure = new IllegalStateException(
                "query failed", new RuntimeException("wrapped", new IllegalArgumentException("Column X not found")));

        assertThat(Errors.rootMessage(failure, 100)).isEqualTo("Column X not found");
    }

    @Test
    void aLongMessageIsClippedWithAnEllipsisAndAShortOneKeptWhole() {
        assertThat(Errors.rootMessage(new RuntimeException("x".repeat(50)), 10)).isEqualTo("x".repeat(10) + "…");
        assertThat(Errors.rootMessage(new RuntimeException("short"), 10)).isEqualTo("short");
        assertThat(Errors.rootMessage(new RuntimeException("x".repeat(10)), 10)).isEqualTo("x".repeat(10));
    }

    /** An emoji is one character to the clip, kept whole or left out, never halved. */
    @Test
    void aClipNeverCutsACharacterInHalf() {
        assertThat(Errors.rootMessage(new RuntimeException("x".repeat(9) + "\uD83D\uDE9A trucks"), 10))
                .isEqualTo("x".repeat(9) + "\uD83D\uDE9A…");
        assertThat(Errors.rootMessage(new RuntimeException("\uD83D\uDE9A".repeat(3)), 3))
                .isEqualTo("\uD83D\uDE9A".repeat(3));
    }

    @Test
    void aCauseWithoutAMessageIsNamedByItsType() {
        assertThat(Errors.rootMessage(new IllegalStateException("wrapped", new NullPointerException()), 40))
                .isEqualTo("NullPointerException");
    }
}
