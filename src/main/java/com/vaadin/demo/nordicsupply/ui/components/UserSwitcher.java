package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;

import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;

/**
 * The rail's user entry: a button with the signed-in employee's initials and name, and a popover above it that lists
 * the employees the demo can sign in as, each with the region they are responsible for. Choosing one signs the
 * session in as them and reloads the page, so every view starts over as that user. The popover is not a child of
 * the rail: Flow adds it to the page, so a rail that folds away on a small window cannot hide it while it is open and
 * holding the page. It closes when the view changes, as the rail does on a small window.
 */
public class UserSwitcher extends Button implements AfterNavigationObserver {

    private final Popover popover;

    public UserSwitcher(CurrentUser currentUser) {
        super(currentUser.get().name(), new Avatar(currentUser.get().name()));
        var current = currentUser.get();
        addClassNames("rail-button", "rail-user");
        addThemeVariants(ButtonVariant.TERTIARY);
        setTooltipText("Signed in as " + current.name() + ", " + current.scope().label() + ". Click to switch.");

        var users = new ListBox<SignedInUser>();
        users.setItems(currentUser.all());
        users.setValue(current);
        users.setRenderer(new ComponentRenderer<>(UserSwitcher::item));
        users.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                currentUser.set(event.getValue());
                getUI().ifPresent(ui -> ui.getPage().reload());
            }
        });

        popover = new Popover(users);
        popover.setTarget(this);
        popover.setModal(true);
        popover.setAriaLabel("Switch user");
        popover.setPosition(PopoverPosition.TOP_START);
        popover.addThemeVariants(PopoverVariant.NO_PADDING);
    }

    /** Closes a list still open from the view before. */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        popover.close();
    }

    /** One employee in the list: the initials, the name, and the region under it. */
    private static Div item(SignedInUser user) {
        var name = new Span(user.name());
        name.addClassName("name");
        var region = new Span(user.scope().label());
        region.addClassName("region");
        var item = new Div(new Avatar(user.name()), new Div(name, region));
        item.addClassName("user-item");
        return item;
    }
}
