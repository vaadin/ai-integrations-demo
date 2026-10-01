package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.shared.Tooltip;

/** A small pulsing marker that points a visitor at the part of the demo worth seeing; the tooltip says why. */
@Tag("poi-indicator")
public class POIIndicator extends Div {

    /** @param text what the tooltip says about the part of the demo the marker points at */
    public POIIndicator(String text) {
        Tooltip.forComponent(this).setText(text);
    }
}
