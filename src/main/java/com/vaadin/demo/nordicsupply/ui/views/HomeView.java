package com.vaadin.demo.nordicsupply.ui.views;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.ui.MainLayout;

/**
 * The entry page: what the demo is, and cards that lead to the documentation, the source and Vaadin Start. It is the
 * readme of the application.
 */
@Route("")
@PageTitle("Home")
@Menu(order = 0, title = "Home", icon = "icons/house.svg")
public class HomeView extends PageView {

    private static final String DOCS = "https://vaadin.com/docs/latest/flow/ai-support";
    private static final String SOURCE = "https://github.com/vaadin/ai-integrations-demo";
    private static final String START = "https://start.vaadin.com";

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    public HomeView(CurrentUser currentUser) {
        super(
                "home",
                "Good " + timeOfDay(LocalTime.now()) + ", " + currentUser.get().name(),
                LocalDate.now().format(formatter),
                "Home");

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
        add(about, readMore, docs);
    }

    /** A card that is one link, opening in a new tab: a title and one line saying what is there. */
    private static Anchor card(String title, String text, String href) {
        var card = new Card();
        card.addThemeVariants(CardVariant.OUTLINED);
        card.setTitle(title, 4);
        var line = new Span(text);
        line.addClassName("text");
        card.add(line);
        var link = new Anchor(href, card);
        link.setTarget(AnchorTarget.BLANK);
        link.addClassName("start-card");
        return link;
    }

    private static String timeOfDay(LocalTime time) {
        int hour = time.getHour();

        if (hour < 12) {
            return "Morning";
        } else if (hour < 17) {
            return "Afternoon";
        } else if (hour < 21) {
            return "Evening";
        } else {
            return "Late Evening";
        }
    }
}
