package com.vaadin.demo.nordicsupply.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.ScrollerVariant;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.menu.MenuConfiguration;

import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.ShownReadmes;
import com.vaadin.demo.nordicsupply.ui.components.Help;
import com.vaadin.demo.nordicsupply.ui.components.POIIndicator;
import com.vaadin.demo.nordicsupply.ui.components.ReadmePopover;
import com.vaadin.demo.nordicsupply.ui.components.UserSwitcher;
import com.vaadin.demo.nordicsupply.ui.views.ActivityLogView;
import com.vaadin.demo.nordicsupply.ui.views.DashboardView;

/**
 * The app shell, the layout of every view: a left rail with the Nordic Supply wordmark, the views that list
 * themselves with {@code @Menu} in the order they are to be tried, and at its foot the Activity Log, Help, the
 * signed-in user, and the Vaadin mark. The log sits at the foot on purpose: it is the audit trail of what the views
 * above it do, not a place to start. No top bar: each view carries its own heading. Help opens the current view's
 * readme, which also opens by itself on the view's first visit in a session; Home has none, it is the readme, so Help
 * is not shown there. The user entry switches between the demo's users.
 */
@Layout
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    /** The company whose back office this is; every view is written for it. */
    public static final String COMPANY = "Nordic Supply";

    private final Help help;

    public MainLayout(ShownReadmes shownReadmes, CurrentUser currentUser) {
        getElement().setAttribute("no-scroll", "");

        var mark = new Div(new SvgIcon("icons/compass.svg"));
        mark.addClassName("mark");
        var name = new Span(COMPANY);
        name.addClassName("name");
        var wordmark = new Div(mark, name);
        wordmark.addClassName("wordmark");

        var nav = new SideNav();
        for (var entry : MenuConfiguration.getMenuEntries()) {
            var item = new SideNavItem(entry.title(), entry.menuClass(), new SvgIcon(entry.icon()));
            if (entry.menuClass() == DashboardView.class) {
                // the entry the demo is built around, marked so that a visitor starts there
                item.setSuffixComponent(
                        new POIIndicator("Start here: the self-service dashboard the demo is built around"));
            }
            nav.addItem(item);
        }
        var scroller = new Scroller(nav);
        scroller.addClassName("rail-nav");
        scroller.addThemeVariants(ScrollerVariant.OVERFLOW_INDICATORS);

        var foot = new SideNav();
        foot.addClassName("rail-foot");
        foot.addItem(new SideNavItem("Activity Log", ActivityLogView.class, new SvgIcon("icons/clipboard-list.svg")));

        help = new Help(shownReadmes, new ReadmePopover());

        var powered = new Anchor("https://vaadin.com", new SvgIcon("icons/vaadin.svg"), new Span("Powered by Vaadin"));
        powered.setTarget(AnchorTarget.BLANK);
        powered.addClassName("powered-by");

        addToDrawer(wordmark, scroller, foot, help, new UserSwitcher(currentUser), powered);
    }

    /**
     * Help follows the view that navigation has just shown, under the page title the view declares. Not
     * {@code MenuConfiguration.getPageHeader}: that goes through the application's {@code PageTitleGenerator}, which
     * adds the application's name for the browser tab.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        var view = getContent();
        var title = view.getClass().getAnnotation(PageTitle.class);
        help.follow(view, title != null ? title.value() : "");
    }
}
