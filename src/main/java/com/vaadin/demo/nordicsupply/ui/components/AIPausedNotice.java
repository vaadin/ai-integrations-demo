package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.icon.SvgIcon;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;

/**
 * A word under a page's heading while the AI answers nobody: it is switched off, or the instance has used its budget
 * for the day. Shown as the page is built, so that a visitor knows before asking; a question asked anyway is refused
 * with the same sentence. The rest of the page works as usual. Nothing while the AI answers. A badge in the theme's
 * accent, the AI's gold, with the AI's sparkle: styled by the theme alone.
 */
public class AIPausedNotice extends Badge {

    public AIPausedNotice(AIBudget budget) {
        setRole("status");
        var paused = budget.paused();
        setVisible(paused.isPresent());
        paused.ifPresent(reason -> {
            setIcon(new SvgIcon("icons/sparkle.svg"));
            setText(reason);
        });
    }
}
