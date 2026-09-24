package com.vaadin.demo.nordicsupply.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

import com.vaadin.demo.nordicsupply.ui.views.HomeView;

/**
 * The app shell: a left rail with the Nordic Supply wordmark, the back-office views, and the Vaadin mark at its foot.
 * No top bar: each view carries its own heading.
 */
public class MainLayout extends AppLayout {

    /** The company whose back office this is; every view is written for it. */
    public static final String COMPANY = "Nordic Supply";

    public MainLayout() {
        var mark = new Div(new SvgIcon("icons/compass.svg"));
        mark.addClassName("mark");
        var name = new Span(COMPANY);
        name.addClassName("name");
        var wordmark = new Div(mark, name);
        wordmark.addClassName("wordmark");

        var nav = new SideNav();
        nav.addItem(new SideNavItem("Home", HomeView.class, new SvgIcon("icons/house.svg")));

        var powered = new Anchor("https://vaadin.com", new SvgIcon("icons/vaadin.svg"), new Span("Powered by Vaadin"));
        powered.setTarget(AnchorTarget.BLANK);
        powered.addClassName("powered-by");

        var drawer = new Div(wordmark, new Scroller(nav), powered);
        drawer.addClassName("drawer");
        addToDrawer(drawer);
    }

    /** Every view is the page's main landmark, for assistive technology to jump to. */
    @Override
    public void setContent(Component content) {
        super.setContent(content);
        if (content != null) {
            content.getElement().setAttribute("role", "main");
        }
    }
}
