package com.vaadin.demo.nordicsupply.ui.views;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vaadin.flow.component.ai.common.SourceExtract;
import com.vaadin.flow.component.ai.common.ValueSource;

/**
 * What the AI reports of where a value came from, read apart: the snippets of the message it read, quoted as written
 * and outlined where they are, and its reasoning, which tells in words how the value follows from them. The form
 * filler has no place for reasoning, so the model gives it as one more extract, with no location and its text opening
 * with {@link #REASONING}; the snippets stay exact quotes, which the outlines rely on to find their words.
 */
final class ValueSources {

    /** What the model opens a value's reasoning with, to tell it from the snippets. */
    static final String REASONING = "Reasoning:";

    private ValueSources() {}

    /**
     * The snippets the value was read from, without the reasoning.
     *
     * @return the extracts quoted from the message, in the order the model gave them
     */
    static List<SourceExtract> snippets(ValueSource source) {
        return source.extracts().stream()
                .filter(extract -> !isReasoning(extract))
                .toList();
    }

    /**
     * How the value follows from the message, in the model's words, without the opening that marks it.
     *
     * @return the reasoning, empty when the model gave none
     */
    static Optional<String> reasoning(ValueSource source) {
        var reasoning = source.extracts().stream()
                .filter(ValueSources::isReasoning)
                .map(extract ->
                        extract.text().strip().substring(REASONING.length()).strip())
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
        return reasoning.isEmpty() ? Optional.empty() : Optional.of(reasoning);
    }

    private static boolean isReasoning(SourceExtract extract) {
        return extract.text() != null
                && extract.text().strip().regionMatches(true, 0, REASONING, 0, REASONING.length());
    }
}
