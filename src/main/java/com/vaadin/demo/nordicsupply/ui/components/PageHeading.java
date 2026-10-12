package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;

public class PageHeading extends HeroTitle {

    public PageHeading(String title, String subtitle, String watermark) {
        super(title, subtitle, watermark);
        var toggle = new DrawerToggle();
        toggle.getElement().setAttribute("slot", "prefix");
        add(toggle);
    }

    private final Div actions = new Div();

    /** Puts the page's own actions at the heading's end, in a row that {@code page.css} spaces. */
    public void addActions(Component... components) {
        if (actions.getParent().isEmpty()) {
            actions.addClassName("page-actions");
            actions.getElement().setAttribute("slot", "suffix");
            add(actions);
        }
        actions.add(components);
    }
}
