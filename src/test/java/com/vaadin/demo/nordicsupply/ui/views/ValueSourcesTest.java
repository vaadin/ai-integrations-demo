package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.vaadin.flow.component.ai.common.ConfidenceLevel;
import com.vaadin.flow.component.ai.common.PageRegion;
import com.vaadin.flow.component.ai.common.Rect;
import com.vaadin.flow.component.ai.common.SourceExtract;
import com.vaadin.flow.component.ai.common.ValueSource;
import org.junit.jupiter.api.Test;

/** A value's source is read apart into the snippets quoted from the message and the reasoning the model gave. */
class ValueSourcesTest {

    private static final SourceExtract SNIPPET =
            new SourceExtract("SO-2026-006830", new PageRegion(1, new Rect(0.4, 0.3, 0.2, 0.03)));

    @Test
    void theReasoningIsToldApartFromTheSnippets() {
        var source = new ValueSource(
                ConfidenceLevel.HIGH,
                List.of(SNIPPET, new SourceExtract("Reasoning: The subject names the order.", null)));

        assertThat(ValueSources.snippets(source)).containsExactly(SNIPPET);
        assertThat(ValueSources.reasoning(source)).hasValue("The subject names the order.");
    }

    @Test
    void theMarkIsReadWhateverItsCaseAndTheSpaceAroundIt() {
        var source = new ValueSource(null, List.of(new SourceExtract("  reasoning:   As the sender wrote.", null)));

        assertThat(ValueSources.snippets(source)).isEmpty();
        assertThat(ValueSources.reasoning(source)).hasValue("As the sender wrote.");
    }

    @Test
    void aSourceWithoutReasoningIsItsSnippets() {
        var source = new ValueSource(ConfidenceLevel.LOW, List.of(SNIPPET, new SourceExtract("Reasoning:", null)));

        assertThat(ValueSources.snippets(source)).containsExactly(SNIPPET);
        assertThat(ValueSources.reasoning(source)).isEmpty();
    }
}
