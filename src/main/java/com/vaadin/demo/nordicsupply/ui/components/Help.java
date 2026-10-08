package com.vaadin.demo.nordicsupply.ui.components;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.SvgIcon;

import com.vaadin.demo.nordicsupply.session.ShownReadmes;

/**
 * The rail's Help button, which opens the current view's readme, with the documentation it links to, in a
 * {@link ReadmePopover}. The rail tells it which view is showing and under what title; every view gets
 * {@code first-visit} for four seconds the first time it is shown since the browser page was loaded, while a view
 * with a readme also shows the button and opens the readme by itself once per session. A view without one, Home, has
 * no Help: the page is the readme. The popover is anchored to this button but is not its child: Flow adds it to the
 * page, so it shows even when the rail is folded away on a small window.
 */
public class Help extends Button {

    private static final int FIRST_VISIT_MILLIS = 3_000;

    private final ShownReadmes shownReadmes;
    private final ReadmePopover popover;
    private final BiConsumer<UI, Runnable> firstVisitScheduler;
    private final Set<String> visited = new HashSet<>();

    /**
     * @param shownReadmes the readmes the session has already opened by itself
     * @param popover the popover the readmes open in
     */
    public Help(ShownReadmes shownReadmes, ReadmePopover popover) {
        this(shownReadmes, popover, Help::openAfterFirstVisit);
    }

    Help(ShownReadmes shownReadmes, ReadmePopover popover, BiConsumer<UI, Runnable> firstVisitScheduler) {
        super("Help", new SvgIcon("icons/life-buoy.svg"));
        this.shownReadmes = shownReadmes;
        this.popover = popover;
        this.firstVisitScheduler = firstVisitScheduler;
        addClassName("rail-button");
        popover.setTarget(this);
    }

    /**
     * Follows the view the rail shows, under its page title: marks its first visit, shows the button for a view with
     * a readme, opens that readme on the first visit, and closes a readme still open from the view before.
     */
    public void follow(Component view, String title) {
        var readme = view instanceof HasReadme withReadme ? withReadme.readme() : null;
        var sections =
                view instanceof HasReadme withReadme ? withReadme.readmeSections() : List.<HasReadme.ReadmeSection>of();
        var readMore = view instanceof HasReadme withReadme ? withReadme.readMore() : List.<HasReadme.Link>of();
        var showHelp = (readme != null || !sections.isEmpty() || !readMore.isEmpty());
        var introduction = readme != null ? readme : view.getClass().getName();
        var firstVisit = visited.add(introduction);
        view.getElement().getClassList().set("first-visit", firstVisit);
        if (firstVisit) {
            view.getElement()
                    .executeJs("setTimeout(() => this.classList.remove('first-visit'), $0)", FIRST_VISIT_MILLIS);
        }
        setVisible(showHelp);
        if (showHelp && shownReadmes.firstTime(introduction)) {
            popover.setReadme(title, readme, sections, readMore);
            firstVisitScheduler.accept(UI.getCurrent(), () -> popover.show(title, readme, sections, readMore));
        } else {
            popover.close();
            if (showHelp) {
                popover.setReadme(title, readme, sections, readMore);
            }
        }
    }

    private static void openAfterFirstVisit(UI ui, Runnable command) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(FIRST_VISIT_MILLIS);
                ui.access(command::run);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
