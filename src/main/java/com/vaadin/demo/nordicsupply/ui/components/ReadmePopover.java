package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;

/** A view's readme, anchored to the Help button in the application drawer. */
public class ReadmePopover extends Popover {

    private final H2 title = new H2();
    private final Markdown body = new Markdown();

    public ReadmePopover() {
        var close = new Button("Close", event -> close());
        close.addThemeVariants(ButtonVariant.PRIMARY);
        add(title, body, close);
        setWidth("30rem");
        setPosition(PopoverPosition.END_TOP);
        setModal(false);
        addThemeVariants(PopoverVariant.ARROW);
        addClassNames("help");
    }

    /** Sets the readme and the title of the view it belongs to. */
    public void setReadme(String title, String readme) {
        this.title.setText(title);
        setAriaLabel(title + " help");
        body.setContent(readme);
    }

    /** Sets and opens the readme. */
    public void show(String title, String readme) {
        setReadme(title, readme);
        open();
    }
}
