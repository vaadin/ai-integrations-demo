package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.SvgIcon;

import com.vaadin.demo.nordicsupply.session.ShownReadmes;

/**
 * The rail's Help button, which opens the current view's readme in a {@link ReadmeDialog}. The rail tells it which
 * view is showing and under what title; a view with a readme shows the button, and the readme opens by itself the
 * first time the view is visited in the session. A view without one, Home, has no Help: the page is the readme. The
 * dialog is not a child of the rail: Flow adds it to the page while it is open, so it shows even when the rail is
 * folded away on a small window.
 */
public class Help extends Button {

    private final ShownReadmes shownReadmes;
    private final ReadmeDialog dialog;
    private String title;
    private String readme;

    /**
     * @param shownReadmes the readmes the session has already opened by itself
     * @param dialog the dialog the readmes open in
     */
    public Help(ShownReadmes shownReadmes, ReadmeDialog dialog) {
        super("Help", new SvgIcon("icons/life-buoy.svg"));
        this.shownReadmes = shownReadmes;
        this.dialog = dialog;
        addClassName("rail-button");
        addThemeVariants(ButtonVariant.TERTIARY);
        addClickListener(event -> dialog.show(title, readme));
    }

    /**
     * Follows the view the rail shows, under its page title: shows the button for a view with a readme, opens the
     * readme on the view's first visit in the session, and closes a readme still open from the view before.
     */
    public void follow(Component view, String title) {
        this.title = title;
        readme = view instanceof HasReadme withReadme ? withReadme.readme() : null;
        setVisible(readme != null);
        if (readme != null && shownReadmes.firstTime(readme)) {
            dialog.show(title, readme);
        } else {
            dialog.close();
        }
    }
}
