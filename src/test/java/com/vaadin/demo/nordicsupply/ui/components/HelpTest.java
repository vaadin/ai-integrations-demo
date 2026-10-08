package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.UnorderedList;
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

    /** A view whose readme points at the documentation of its components. */
    private static class DocumentedView extends ReadmeView {

        DocumentedView() {
            super("Dashboard");
        }

        @Override
        public List<Link> readMore() {
            return List.of(new Link("AI-powered Grid", "https://vaadin.com/docs/grid", "the table widget"));
        }
    }

    private static class SectionedView extends Div implements HasReadme {

        private final String section;

        SectionedView(String section) {
            this.section = section;
        }

        @Override
        public String readme() {
            return null;
        }

        @Override
        public List<ReadmeSection> readmeSections() {
            return List.of(new ReadmeSection(section, "The details."));
        }
    }

    private static final class ClaimsView extends SectionedView {

        ClaimsView() {
            super("Claims");
        }
    }

    private static final class DashboardView extends SectionedView {

        DashboardView() {
            super("Dashboard");
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
        var help = help(session, popover);
        var view = new ReadmeView("Orders");

        help.follow(view, "Orders");

        assertThat(popover.isOpened()).isTrue();
        assertThat(popover.getTarget()).isSameAs(help);
        assertThat(help.isVisible()).isTrue();
        assertThat(view.getClassNames()).contains("first-visit");
    }

    @Test
    void aViewsDocumentationLinksAreShownWithItsReadme() {
        var help = help(session, popover);

        help.follow(new DocumentedView(), "Dashboard");

        assertThat(descendants(popover)
                        .filter(UnorderedList.class::isInstance)
                        .flatMap(Component::getChildren)
                        .map(item -> (Anchor) item.getChildren().findFirst().orElseThrow())
                        .map(Anchor::getHref))
                .containsExactly("https://vaadin.com/docs/grid");
    }

    /** On a later visit the readme is not opened but set, links included, for Help to open. */
    @Test
    void aLaterVisitSetsTheReadmeAndItsLinksForHelpToOpen() {
        var help = help(session, new ReadmePopover());
        help.follow(new DocumentedView(), "Dashboard");

        var reloadedPage = help(session, popover);
        reloadedPage.follow(new DocumentedView(), "Dashboard");

        assertThat(popover.isOpened()).isFalse();
        assertThat(descendants(popover).filter(UnorderedList.class::isInstance).flatMap(Component::getChildren))
                .hasSize(1);
    }

    @Test
    void thePopoverIsAnchoredToTheHelpButtonButLivesOnThePage() {
        var help = help(session, popover);

        ui.add(help);

        assertThat(popover.getTarget()).isSameAs(help);
        assertThat(help.getChildren()).noneMatch(ReadmePopover.class::isInstance);
        assertThat(ui.getChildren()).anyMatch(ReadmePopover.class::isInstance);
    }

    @Test
    void reloadingThePageRestoresTheClassButDoesNotReopenTheReadmeInTheSameSession() {
        var firstTab = help(session, new ReadmePopover());
        firstTab.follow(new ReadmeView("Orders"), "Orders");

        var reloadedPage = help(session, popover);
        var firstVisitAfterReload = new ReadmeView("Orders");
        reloadedPage.follow(firstVisitAfterReload, "Orders");

        assertThat(popover.isOpened()).isFalse();
        assertThat(reloadedPage.isVisible()).isTrue();
        assertThat(firstVisitAfterReload.getClassNames()).contains("first-visit");
    }

    @Test
    void eachViewsReadmeOpensOnItsOwnFirstVisit() {
        var help = help(session, popover);
        help.follow(new ReadmeView("Orders"), "Orders");
        popover.close();

        help.follow(new ReadmeView("Customers"), "Customers");

        assertThat(popover.isOpened()).isTrue();
    }

    @Test
    void eachSectionOnlyReadmeOpensOnItsOwnFirstVisit() {
        var help = help(session, popover);
        help.follow(new ClaimsView(), "Claims");
        popover.close();

        help.follow(new DashboardView(), "Dashboard");

        assertThat(popover.isOpened()).isTrue();
        assertThat(popover.getAriaLabel()).contains("Dashboard help");
    }

    @Test
    void theButtonTargetsTheCurrentViewsReadmeAfterItsFirstVisit() {
        var help = help(session, popover);
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
        var help = help(session, popover);
        var view = new Div();

        help.follow(view, "Home");

        assertThat(popover.isOpened()).isFalse();
        assertThat(help.isVisible()).isFalse();
        assertThat(view.getClassNames()).contains("first-visit");
    }

    @Test
    void aViewWithoutAReadmeOnlyHasTheClassOnItsFirstVisit() {
        var help = help(session, popover);
        help.follow(new Div(), "Home");
        var laterVisit = new Div();

        help.follow(laterVisit, "Home");

        assertThat(laterVisit.getClassNames()).doesNotContain("first-visit");
    }

    @Test
    void helpReturnsWithTheNextViewThatHasAReadme() {
        var help = help(session, popover);
        help.follow(new Div(), "Home");

        help.follow(new ReadmeView("Orders"), "Orders");

        assertThat(help.isVisible()).isTrue();
    }

    @Test
    void aReadmeStillOpenClosesWhenTheViewChanges() {
        var help = help(session, popover);
        help.follow(new ReadmeView("Orders"), "Orders");

        help.follow(new Div(), "Home");

        assertThat(popover.isOpened()).isFalse();
    }

    private Help help(ShownReadmes shownReadmes, ReadmePopover readmePopover) {
        return new Help(shownReadmes, readmePopover, (currentUi, command) -> command.run());
    }

    private java.util.stream.Stream<Component> descendants(Component root) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
