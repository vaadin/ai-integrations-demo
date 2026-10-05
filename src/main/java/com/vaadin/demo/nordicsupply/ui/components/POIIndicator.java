package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;

/** A small pulsing marker that points a visitor at the part of the demo worth seeing; the tooltip says why. */
public class POIIndicator extends Span {

    /** @param text what the tooltip says about the part of the demo the marker points at */
    public POIIndicator(String text) {
        addClassName("poi-indicator");
        Tooltip.forComponent(this).setText(text);
    }
}
