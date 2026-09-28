package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.SvgIcon;

import com.vaadin.demo.nordicsupply.session.ShownReadmes;

/**
 * The rail's Help: a button that opens the current view's {@link Readme} in a {@link ReadmeDialog}. The rail tells it
 * which view is showing; a view with a readme shows the button, and the readme opens by itself the first time the
 * view is visited in the session. A view without one, Home, has no Help: the page is the readme. The dialog is not a child of
 * the rail: Flow adds it to the page while it is open, so it shows even when the rail is folded away on a small
 * window.
 */
public class Help extends Div {

    private final ShownReadmes shownReadmes;
    private final ReadmeDialog dialog;
    private final Button button;
    private Readme readme;

    /**
     * @param shownReadmes the readmes the session has already opened by itself
     * @param dialog the dialog the readmes open in
     */
    public Help(ShownReadmes shownReadmes, ReadmeDialog dialog) {
        this.shownReadmes = shownReadmes;
        this.dialog = dialog;
        button = new Button("Help", new SvgIcon("icons/life-buoy.svg"), e -> dialog.show(readme));
        button.addClassName("rail-button");
        button.addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("rail-help");
        add(button);
    }

    /**
     * Follows the view the rail shows: shows the button for a view with a readme, opens the readme on the view's
     * first visit in the session, and closes a readme still open from the view before.
     */
    public void follow(Component view) {
        readme = view instanceof HasReadme withReadme ? withReadme.readme() : null;
        setVisible(readme != null);
        if (readme != null && shownReadmes.firstTime(readme)) {
            dialog.show(readme);
        } else {
            dialog.close();
        }
    }
}
