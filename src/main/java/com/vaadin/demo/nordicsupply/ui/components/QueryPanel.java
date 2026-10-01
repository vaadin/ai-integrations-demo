package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;

import com.vaadin.demo.nordicsupply.domain.WidgetType;

/**
 * The "ask a question" panel of Insights: a prompt bar with an arrow, a "Render as" toggle between chart and table
 * under it, suggested questions, and the privacy footnote. It sits in the middle of an empty dashboard and, once
 * widgets exist, inside the "New Query" tile at the end of the dashboard, where it stacks its parts to fit one cell.
 */
public class QueryPanel extends Div {

    /** About five lines; a longer question scrolls inside the field instead of pushing the chips off the screen. */
    private static final String MAX_QUESTION_HEIGHT = "9rem";

    /** What the panel promises about the data: the model is shown the schema, never the rows. */
    public static final String FOOTNOTE = "Schema-only queries · No data leaves your network";

    /** What to ask, in the middle of an empty dashboard, where the field has the room. */
    private static final String PLACEHOLDER =
            "Ask a question about orders, shipments, claims or customers, in your own words";

    /**
     * The same, short enough for the New Query tile, where the field shares the bar with the arrow: it fits the
     * field's two lines, and a placeholder longer than those grows the field and pushes the chips against the tile's
     * lower edge.
     */
    private static final String TILE_PLACEHOLDER = "Ask about orders, shipments, claims or customers";

    /** Said under the field when a question is over the cap; the field keeps the text for the user to shorten. */
    private static final String TOO_LONG = "At most " + InsightWidget.MAX_QUESTION_LENGTH + " characters";

    /** Where the panel sits; a tile has less room and stacks the bar. */
    public enum Mode {
        CENTER,
        TILE
    }

    /** Grows with the question up to a few lines; Enter sends, Shift+Enter starts a new line. */
    private final TextArea question = new TextArea();

    /** What a question asks for: a widget of either kind, a chart or, in the analyst's word, a table. */
    private final RadioButtonGroup<WidgetType> type = new RadioButtonGroup<>("Render as");

    private final FlexLayout tryChips = new FlexLayout();

    private final Map<WidgetType, List<String>> chips;
    private final BiConsumer<String, WidgetType> onAsk;

    /**
     * @param chips the suggested questions per kind of request; a kind without an entry shows none
     * @param onAsk receives the question and the kind the user chose
     */
    public QueryPanel(Map<WidgetType, List<String>> chips, BiConsumer<String, WidgetType> onAsk) {
        this.chips = chips;
        this.onAsk = onAsk;
        addClassName("query-panel");

        question.setClearButtonVisible(true);
        question.setWidthFull();
        question.setMaxHeight(MAX_QUESTION_HEIGHT);
        question.setMaxLength(InsightWidget.MAX_QUESTION_LENGTH);
        question.setI18n(new TextArea.TextAreaI18n().setMaxLengthErrorMessage(TOO_LONG));
        var send = new Button(VaadinIcon.ARROW_RIGHT.create(), e -> submit(question.getValue()));
        send.addThemeVariants(ButtonVariant.PRIMARY);
        send.setAriaLabel("Ask");
        // Enter sends; a shortcut matches the key without modifiers, so Shift+Enter stays a line break in the field.
        // The field's value is flushed to the server before the shortcut runs, so nothing typed is lost.
        Shortcuts.addShortcutListener(question, () -> submit(question.getValue()), Key.ENTER)
                .listenOn(question)
                .resetFocusOnActiveElement();
        var bar = new HorizontalLayout(question, send);
        bar.addClassName("prompt-bar");
        bar.setAlignItems(FlexLayout.Alignment.CENTER);
        bar.expand(question);

        // a chart is the default: it is what a question about "by week" or "by carrier" is usually after
        type.setItems(WidgetType.CHART, WidgetType.GRID);
        type.setItemLabelGenerator(kind -> kind == WidgetType.CHART ? "Chart" : "Table");
        type.setValue(WidgetType.CHART);
        type.addClassName("prompt-type");
        type.addClassName("segmented");

        tryChips.addClassName("chips");
        tryChips.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        tryChips.setAlignItems(FlexLayout.Alignment.CENTER);
        type.addValueChangeListener(e -> showChips());
        showChips();
        var footnote = new Span(FOOTNOTE);
        footnote.addClassName("footnote");
        add(bar, type, tryChips, footnote);
        setMode(Mode.CENTER);
    }

    /** Moves the panel between its two places; the styles read the mode from the element's {@code mode} attribute. */
    public void setMode(Mode mode) {
        getElement().setAttribute("mode", mode.name().toLowerCase(Locale.ROOT));
        question.setPlaceholder(mode == Mode.TILE ? TILE_PLACEHOLDER : PLACEHOLDER);
    }

    /** The suggestions follow the kind of request: a table and a chart want different examples. */
    private void showChips() {
        tryChips.removeAll();
        var kind = type.getValue();
        var suggestions = chips.getOrDefault(kind, List.of());
        tryChips.setVisible(!suggestions.isEmpty());
        tryChips.add(new Span("Try:"));
        for (var chip : suggestions) {
            var b = new Button(chip, e -> onAsk.accept(chip, kind));
            b.addThemeVariants(ButtonVariant.SMALL);
            tryChips.add(b);
        }
    }

    private void submit(String value) {
        var text = value == null ? "" : value.trim();
        // the field checks its own length on the server, whatever the client let through; a value over the cap
        // stays in it, marked invalid with the message
        if (text.isEmpty() || question.isInvalid()) {
            question.focus();
            return;
        }
        question.clear();
        onAsk.accept(text, type.getValue());
    }
}
