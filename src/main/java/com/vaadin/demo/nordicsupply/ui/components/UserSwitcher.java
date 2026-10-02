package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.signals.Signal;

import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;

/**
 * The rail's user entry: a button with the signed-in employee's initials and name, and a popover above it that lists
 * the employees the demo can sign in as, each with the region they are responsible for. Choosing one signs the
 * session in as them, and every tab of the session reloads its page, so every view starts over as that user. The
 * popover is not a child of the rail: Flow adds it to the page, so a rail that folds away on a small window cannot
 * hide it while it is open and holding the page. It closes when the view changes, as the rail does on a small window.
 */
public class UserSwitcher extends Button implements AfterNavigationObserver {

    private final Popover popover;

    public UserSwitcher(CurrentUser currentUser) {
        super(currentUser.get().name(), new Avatar(currentUser.get().name()));
        var user = currentUser.get();
        addClassNames("rail-button");
        var dropdownIcon = new SvgIcon("icons/chevron-down.svg");
        dropdownIcon.addClassName("dropdown");
        setSuffixComponent(dropdownIcon);

        var users = new ListBox<SignedInUser>();
        users.setItems(currentUser.all());
        users.setValue(user);
        users.setRenderer(new ComponentRenderer<>(UserSwitcher::item));
        users.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                currentUser.set(event.getValue());
            }
        });

        popover = new Popover(users);
        popover.setTarget(this);
        popover.setModal(true);
        popover.setAriaLabel("Switch user");
        popover.setPosition(PopoverPosition.TOP_START);

        // a switch, in this tab or another of the session, reloads the page: every view starts over as the new user
        Signal.effect(this, context -> {
            currentUser.signal().get();
            if (!context.isInitialRun()) {
                UI.getCurrent().getPage().reload();
            }
        });
    }

    /** Closes a list still open from the view before. */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        popover.close();
    }

    /** One employee in the list: the initials, the name, and the region under it. */
    private static Div item(SignedInUser user) {
        var name = new Div(user.name());
        name.addClassName("name");
        var region = new Div(user.scope().label());
        region.addClassName("region");
        var item = new Div(new Avatar(user.name()), new Div(name, region));
        item.addClassName("user-item");
        return item;
    }
}
