package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.session.ShownReadmes;

/** Help opens a view's readme on the view's first visit in a session, and again when pressed. */
class HelpTest {

    /** A view with a readme, as the list views are. */
    private static class ReadmeView extends Div implements HasReadme {

        private final String readme;

        ReadmeView(String name) {
            readme = "The " + name + " list.";
        }

        @Override
        public String readme() {
            return readme;
        }
    }

    private final ShownReadmes session = new ShownReadmes();
    private final ReadmePopover popover = new ReadmePopover();

    /** Held here: the current UI is kept only weakly, so a garbage collection would otherwise clear it mid-test. */
    private final UI ui = new UI();

    @BeforeEach
    void aUi() {
        UI.setCurrent(ui);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void aViewsReadmeOpensByItselfOnItsFirstVisitUnderTheViewsTitle() {
        var help = new Help(session, popover);
        var view = new ReadmeView("Orders");

        help.follow(view, "Orders");

        assertThat(popover.isOpened()).isTrue();
        assertThat(popover.getTarget()).isSameAs(help);
        assertThat(help.isVisible()).isTrue();
        assertThat(view.getClassNames()).contains("first-visit");
    }

    @Test
    void thePopoverIsAnchoredToTheHelpButtonButLivesOnThePage() {
        var help = new Help(session, popover);

        ui.add(help);

        assertThat(popover.getTarget()).isSameAs(help);
        assertThat(help.getChildren()).noneMatch(ReadmePopover.class::isInstance);
        assertThat(ui.getChildren()).anyMatch(ReadmePopover.class::isInstance);
    }

    @Test
    void reloadingThePageRestoresTheClassButDoesNotReopenTheReadmeInTheSameSession() {
        var firstTab = new Help(session, new ReadmePopover());
        firstTab.follow(new ReadmeView("Orders"), "Orders");

        var reloadedPage = new Help(session, popover);
        var firstVisitAfterReload = new ReadmeView("Orders");
        reloadedPage.follow(firstVisitAfterReload, "Orders");

        assertThat(popover.isOpened()).isFalse();
        assertThat(reloadedPage.isVisible()).isTrue();
        assertThat(firstVisitAfterReload.getClassNames()).contains("first-visit");
    }

    @Test
    void eachViewsReadmeOpensOnItsOwnFirstVisit() {
        var help = new Help(session, popover);
        help.follow(new ReadmeView("Orders"), "Orders");
        popover.close();

        help.follow(new ReadmeView("Customers"), "Customers");

        assertThat(popover.isOpened()).isTrue();
    }

    @Test
    void theButtonTargetsTheCurrentViewsReadmeAfterItsFirstVisit() {
        var help = new Help(session, popover);
        help.follow(new ReadmeView("Orders"), "Orders");
        popover.close();
        var laterVisit = new ReadmeView("Orders");

        help.follow(laterVisit, "Orders");

        assertThat(popover.getTarget()).isSameAs(help);
        assertThat(popover.isOpenOnClick()).isTrue();
        assertThat(laterVisit.getClassNames()).doesNotContain("first-visit");
    }

    @Test
    void aViewWithoutAReadmeOpensNothingAndHidesHelp() {
        var help = new Help(session, popover);
        var view = new Div();

        help.follow(view, "Home");

        assertThat(popover.isOpened()).isFalse();
        assertThat(help.isVisible()).isFalse();
        assertThat(view.getClassNames()).contains("first-visit");
    }

    @Test
    void aViewWithoutAReadmeOnlyHasTheClassOnItsFirstVisit() {
        var help = new Help(session, popover);
        help.follow(new Div(), "Home");
        var laterVisit = new Div();

        help.follow(laterVisit, "Home");

        assertThat(laterVisit.getClassNames()).doesNotContain("first-visit");
    }

    @Test
    void helpReturnsWithTheNextViewThatHasAReadme() {
        var help = new Help(session, popover);
        help.follow(new Div(), "Home");

        help.follow(new ReadmeView("Orders"), "Orders");

        assertThat(help.isVisible()).isTrue();
    }

    @Test
    void aReadmeStillOpenClosesWhenTheViewChanges() {
        var help = new Help(session, popover);
        help.follow(new ReadmeView("Orders"), "Orders");

        help.follow(new Div(), "Home");

        assertThat(popover.isOpened()).isFalse();
    }
}
