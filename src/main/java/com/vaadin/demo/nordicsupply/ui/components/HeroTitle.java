package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

@Tag("hero-title")
@JavaScript("./hero-title.js")
public class HeroTitle extends Div {
    public HeroTitle(String title, String subtitle, String watermark) {
        var titleEl = new Div(title);
        titleEl.getElement().setAttribute("slot", "title");
        var subtitleEl = new Div(subtitle);
        subtitleEl.getElement().setAttribute("slot", "subtitle");
        this.add(titleEl, subtitleEl);
        this.getElement().setAttribute("deco-text", watermark);
    }
}
