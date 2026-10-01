package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.domain.WidgetType;

/** The prompt sends a question with the kind of widget chosen, and suggests questions that fit that kind. */
class QueryPanelTest {

    private record Asked(String question, WidgetType kind) {}

    private final List<Asked> asked = new ArrayList<>();
    private final QueryPanel panel = new QueryPanel(
            Map.of(
                    WidgetType.GRID, List.of("Claims open over 7 days"),
                    WidgetType.CHART, List.of("Orders per month", "Open claims by type")),
            (question, kind) -> asked.add(new Asked(question, kind)));

    @Test
    void aQuestionIsSentTrimmedWithTheKindChosenAndTheFieldCleared() {
        question().setValue("  Late shipments last month  ");

        send().click();

        assertThat(asked).containsExactly(new Asked("Late shipments last month", WidgetType.CHART));
        assertThat(question().getValue()).isEmpty();
    }

    @Test
    void anEmptyQuestionSendsNothing() {
        question().setValue("   ");

        send().click();

        assertThat(asked).isEmpty();
    }

    /** A chart is the default: it is what a question about "by week" or "by carrier" is usually after. */
    @Test
    void theKindIsAChartByDefaultAndCanBeATable() {
        assertThat(kind().getValue()).isEqualTo(WidgetType.CHART);

        kind().setValue(WidgetType.GRID);
        question().setValue("Claims");
        send().click();

        assertThat(asked).containsExactly(new Asked("Claims", WidgetType.GRID));
    }

    /** The kinds read as "Render as" offers them: a chart, and a grid in the analyst's word, a table. */
    @Test
    void theKindsReadChartAndTable() {
        assertThat(kind().getListDataView().getItems().map(kind().getItemLabelGenerator()))
                .containsExactly("Chart", "Table");
    }

    @Test
    void theSuggestionsFollowTheKindAndSendTheirTextAsTheQuestion() {
        assertThat(chips()).extracting(Button::getText).containsExactly("Orders per month", "Open claims by type");

        kind().setValue(WidgetType.GRID);
        assertThat(chips()).extracting(Button::getText).containsExactly("Claims open over 7 days");

        chips().getFirst().click();
        assertThat(asked).containsExactly(new Asked("Claims open over 7 days", WidgetType.GRID));
    }

    /** Every question is a paid model call, so the field takes a sentence or two and no more. */
    @Test
    void aQuestionStopsAtFiveHundredCharacters() {
        assertThat(question().getMaxLength()).isEqualTo(500);
    }

    /**
     * The cap holds on the server too, whatever a client sends: a longer question is not asked and stays in the field,
     * which says why, for the user to shorten it. One at the cap is asked.
     */
    @Test
    void aQuestionOverTheCapStaysInTheFieldWithAMessageAndIsNotAsked() {
        question().setValue("x".repeat(501));
        send().click();

        assertThat(asked).isEmpty();
        assertThat(question().isInvalid()).isTrue();
        assertThat(question().getErrorMessage()).isEqualTo("At most 500 characters");
        assertThat(question().getValue()).hasSize(501);

        question().setValue("x".repeat(500));
        send().click();

        assertThat(asked).singleElement().extracting(Asked::question).asString().hasSize(500);
        assertThat(question().isInvalid()).isFalse();
    }

    /** The panel says where it sits, and in the tile, where the field shares the bar, it asks in fewer words. */
    @Test
    void thePanelSaysWhereItSits() {
        assertThat(panel.getElement().getAttribute("mode")).isEqualTo("center");
        assertThat(question().getPlaceholder())
                .isEqualTo("Ask a question about orders, shipments, claims or customers, in your own words");

        panel.setMode(QueryPanel.Mode.TILE);

        assertThat(panel.getElement().getAttribute("mode")).isEqualTo("tile");
        assertThat(question().getPlaceholder()).isEqualTo("Ask about orders, shipments, claims or customers");
    }

    private TextArea question() {
        return descendants()
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** The arrow beside the question: the one button that is not a suggestion. */
    private Button send() {
        return descendants()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> !chips().contains(button))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private RadioButtonGroup<WidgetType> kind() {
        return descendants()
                .filter(RadioButtonGroup.class::isInstance)
                .map(c -> (RadioButtonGroup<WidgetType>) c)
                .findFirst()
                .orElseThrow();
    }

    private List<Button> chips() {
        return descendants()
                .filter(c -> c.getParent().map(p -> p.hasClassName("chips")).orElse(false))
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private Stream<Component> descendants() {
        return descendants(panel);
    }

    private static Stream<Component> descendants(Component root) {
        return root.getChildren().flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
