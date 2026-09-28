package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.markdown.Markdown;

/**
 * The dialog a readme is read in: the view's title as the header, the Markdown as the body, a Close button in the
 * footer. {@link Help} keeps one and shows the current view's readme in it.
 */
public class ReadmeDialog extends Dialog {

    private final Markdown body = new Markdown();

    public ReadmeDialog() {
        setWidth("42rem"); // a reading measure for the text; Aura keeps the dialog inside a narrower viewport
        setMaxHeight("85vh");
        add(body);
        var close = new Button("Close", e -> close());
        close.addThemeVariants(ButtonVariant.PRIMARY);
        getFooter().add(close);
    }

    /** Opens the dialog on a readme, under the title of the view it belongs to. */
    public void show(String title, String readme) {
        setHeaderTitle(title);
        body.setContent(readme);
        open();
    }
}
