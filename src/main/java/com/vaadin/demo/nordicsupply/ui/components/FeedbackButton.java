package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.SvgIcon;

public class FeedbackButton extends Button {

    public FeedbackButton(FeedbackDialog dialog) {
        super("Demo Feedback", new SvgIcon("icons/message-square.svg"));
        addClassName("rail-button");
        getElement().setAttribute("aria-haspopup", "dialog");
        addClickListener(event -> dialog.open());
    }
}
