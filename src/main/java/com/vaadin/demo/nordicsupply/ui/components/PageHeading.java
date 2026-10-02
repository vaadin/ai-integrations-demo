package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;

/**
 * The heading every page carries: the title as the page's {@code h1} in heavy italic capitals, a gold subtitle under
 * it, and the same word as a large translucent watermark behind the top of the page. The page's {@code page} class
 * clips the watermark at the page's edge and lifts the content above it. The drawer toggle shows only when the rail
 * is collapsed into an overlay.
 */
public class PageHeading extends Div {

    public PageHeading(String title, String subtitle, String watermark) {
        addClassName("page-heading");
        add(new DrawerToggle(), new HeroTitle(title, subtitle, watermark));
    }
}
