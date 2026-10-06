package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;

/**
 * A word under a page's heading while the AI answers nobody: it is switched off, or the instance has used its budget
 * for the day. Shown as the page is built, so that a visitor knows before asking; a question asked anyway is refused
 * with the same sentence. The rest of the page works as usual. Nothing while the AI answers.
 */
public class AIPausedNotice extends Div {

    public AIPausedNotice(AIBudget budget) {
        addClassName("ai-notice");
        getElement().setAttribute("role", "status");
        var paused = budget.paused();
        setVisible(paused.isPresent());
        paused.ifPresent(reason -> {
            var sparkle = new Span(new SvgIcon("icons/sparkle.svg"));
            sparkle.addClassName("sparkle");
            add(sparkle, new Span(reason));
        });
    }
}
