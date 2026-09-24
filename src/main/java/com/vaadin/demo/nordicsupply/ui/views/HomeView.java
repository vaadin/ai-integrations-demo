package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.vaadin.demo.nordicsupply.ui.MainLayout;
import com.vaadin.demo.nordicsupply.ui.components.PageHeading;

/** The entry page of the demo: what the application is, and cards with where to go from here. */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Home")
public class HomeView extends VerticalLayout {

    private static final String DOCS = "https://vaadin.com/docs/latest/flow/ai-support";
    private static final String SOURCE = "https://github.com/vaadin/ai-integrations-demo";
    private static final String START = "https://start.vaadin.com";

    public HomeView() {
        setSizeFull();
        setPadding(true);
        addClassName("page");
        var heading = new PageHeading(MainLayout.COMPANY, "Order desk");

        var aboutTitle = new H2("About this demo");
        aboutTitle.addClassName("about-title");
        var intro = new Paragraph(MainLayout.COMPANY + " is a fictional outdoor-equipment wholesaler, and this "
                + "application is its back office, built to showcase Vaadin AI integrations. The orders, "
                + "claims and catalogue are generated data; the AI features call a live model.");
        intro.addClassName("intro");
        var about = new Div(aboutTitle, intro);
        about.addClassName("about");

        var readMore = new H3("Read more");
        readMore.addClassName("section-title");
        var docs = new Div(
                card("AI support in Vaadin Flow", "The documentation of Vaadin's AI components.", DOCS),
                card("Source code", "This application's code, to read, clone and run.", SOURCE),
                card(
                        "Start a project",
                        "Generate a Vaadin project of your own and add the AI components to it.",
                        START));
        docs.addClassName("start-cards");
        add(heading, about, readMore, docs);
    }

    /** A card that is one link, opening in a new tab: a title and one line saying what is there. */
    private static Anchor card(String title, String text, String href) {
        var titleSpan = new Span(title);
        titleSpan.addClassName("title");
        var textSpan = new Span(text);
        textSpan.addClassName("text");
        var card = new Anchor(href, titleSpan, textSpan);
        card.setTarget(AnchorTarget.BLANK);
        card.addClassName("start-card");
        return card;
    }
}
