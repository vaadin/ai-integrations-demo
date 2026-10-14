package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.demo.nordicsupply.domain.WidgetType;

/**
 * The "ask a question" panel of Dashboard: a prompt bar with an arrow, a "Render as" toggle between chart and table
 * under it, suggested questions, and the privacy footnote. A suggestion only fills in the question: the user reads
 * it, may change it, and sends it, since every question sent is a paid model call. It sits in the middle of an empty
 * dashboard and, once widgets exist, inside the "New Query" tile at the end of the dashboard, where it stacks its
 * parts to fit one cell.
 */
public class QueryPanel extends Div {


    /** What the panel promises about the data: the model is shown the schema, never the rows. */
    public static final String FOOTNOTE = "Schema-only queries · No data leaves your network";
    /**
     * Prompt placeholder.
     */
    private static final String PLACEHOLDER = "Ask about orders, shipments, claims or customers";

    /** Grows with the question up to a few lines; Enter sends, Shift+Enter starts a new line. */
    private final TextArea question = new TextArea();

    /** What a question asks for: a widget of either kind, a chart or, in the analyst's word, a table. */
    private final RadioButtonGroup<WidgetType> type = new RadioButtonGroup<>();

    private final Div suggestionButtons = new Div();

    private final Map<WidgetType, List<String>> chips;
    private final BiConsumer<String, WidgetType> onAsk;

    /**
     * @param chips the suggested questions per kind of request; a kind without an entry shows none
     * @param maxQuestionLength the longest question the field takes, the budget's cap; the field says so under itself
     *     when a question is over it, and keeps the text for the user to shorten
     * @param onAsk receives the question and the kind the user chose
     */
    public QueryPanel(
            Map<WidgetType, List<String>> chips, int maxQuestionLength, BiConsumer<String, WidgetType> onAsk) {
        this.chips = chips;
        this.onAsk = onAsk;
        addClassName("query-panel");

        question.setPlaceholder(PLACEHOLDER);
        question.setMaxLength(maxQuestionLength);
        question.setI18n(new TextArea.TextAreaI18n().setMaxLengthErrorMessage(
                "At most " + maxQuestionLength + " characters"));
        question.setMinRows(1);
        question.setMaxRows(7);
        var send = new Button(new SvgIcon("icons/arrow-up.svg"), e -> submit(question.getValue()));
        send.setAriaLabel("Ask");
        send.setEnabled(false);
        send.addThemeVariants(ButtonVariant.SMALL);
        // Enter sends; a shortcut matches the key without modifiers, so Shift+Enter stays a line break in the field.
        // The field's value is flushed to the server before the shortcut runs, so nothing typed is lost.
        Shortcuts.addShortcutListener(question, () -> submit(question.getValue()), Key.ENTER)
                .listenOn(question)
                .resetFocusOnActiveElement();
        question.setValueChangeMode(ValueChangeMode.EAGER);
        question.addValueChangeListener(e -> {
            if (question.getValue().isEmpty()) {
                send.removeThemeVariants(ButtonVariant.PRIMARY);
                send.setEnabled(false);
            } else {
                send.addThemeVariants(ButtonVariant.PRIMARY);
                send.setEnabled(true);
            }
        });
        question.setAutofocus(true);

        // a chart is the default: it is what a question about "by week" or "by carrier" is usually after
        type.setAriaLabel("Render resulting widget as");
        type.setItems(WidgetType.CHART, WidgetType.GRID);
        type.setItemLabelGenerator(kind -> kind == WidgetType.CHART ? "Chart" : "Table");
        type.setValue(WidgetType.CHART);
        type.addClassName("prompt-type");
        type.setTooltipText("Resulting widget type");
        // the group is an Aura surface, as fields are, for its segments' pill to take the fields' colour
        type.addClassNames("segmented", "aura-surface");
        type.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        type.addThemeName("small");

        type.addValueChangeListener(e -> showChips());
        suggestionButtons.addClassName("suggestions");
        showChips();
        var footnote = new Span(FOOTNOTE);
        footnote.addClassName("footnote");

        question.setAriaLabel("Create New Widget");
        var visibleLabel = new Div("Create New Widget");
        visibleLabel.getElement().setAttribute("aria-hidden", "true");
        question.setPrefixComponent(visibleLabel);
        question.setSuffixComponent(new Div(type, send));
        add(suggestionButtons, question, footnote);
    }

    private void showChips() {
        suggestionButtons.removeAll();
        var kind = type.getValue();
        var suggestions = chips.getOrDefault(kind, List.of());
        suggestionButtons.setVisible(!suggestions.isEmpty());
        for (var prompt : suggestions) {
            var b = new Button(prompt, e -> suggest(prompt));
            b.addThemeVariants(ButtonVariant.SMALL);
            b.addClassName("aura-accent-neutral");
            suggestionButtons.add(b);
        }
    }

    /** Puts a suggestion in the field, in place of what it held, with the cursor after it, ready to send or change. */
    private void suggest(String chip) {
        question.setValue(chip);
        question.focus();
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
