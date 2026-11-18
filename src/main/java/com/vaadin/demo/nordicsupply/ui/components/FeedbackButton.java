package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;

public class FeedbackButton extends Button {

    public FeedbackButton(FeedbackDialog dialog) {
        setPrefixComponent(new SvgIcon("icons/message-square.svg"));
        var title = new Span("Give Feedback");
        title.addClassName("title");
        var hint = new Span("Tell the Vaadin team about this demo");
        hint.addClassName("hint");
        // Button has no method to add child components
        getElement().appendChild(title.getElement(), hint.getElement());
        addThemeVariants(ButtonVariant.PRIMARY);
        addClassName("feedback-button");
        getElement().setAttribute("aria-haspopup", "dialog");
        addClickListener(event -> dialog.open());
    }
}
