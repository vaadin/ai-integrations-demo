package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.session.ShownReadmes;

/** Help opens a view's readme on the view's first visit in a session, and again when pressed. */
class HelpTest {

    /** A view with a readme, as the list views are. */
    private static class ReadmeView extends Div implements HasReadme {

        private final Readme readme;

        ReadmeView(String title) {
            readme = new Readme(title, "The " + title + " list.");
        }

        @Override
        public Readme readme() {
            return readme;
        }
    }

    private final ShownReadmes session = new ShownReadmes();
    private final ReadmeDialog dialog = new ReadmeDialog();

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
    void aViewsReadmeOpensByItselfOnItsFirstVisit() {
        var help = new Help(session, dialog);

        help.follow(new ReadmeView("Orders"));

        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Orders");
        assertThat(help.isVisible()).isTrue();
    }

    @Test
    void theReadmeStaysClosedOnALaterVisitInTheSameSession() {
        var firstTab = new Help(session, new ReadmeDialog());
        firstTab.follow(new ReadmeView("Orders"));

        var secondTab = new Help(session, dialog);
        secondTab.follow(new ReadmeView("Orders"));

        assertThat(dialog.isOpened()).isFalse();
        assertThat(secondTab.isVisible()).isTrue();
    }

    @Test
    void eachViewsReadmeOpensOnItsOwnFirstVisit() {
        var help = new Help(session, dialog);
        help.follow(new ReadmeView("Orders"));
        dialog.close();

        help.follow(new ReadmeView("Customers"));

        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Customers");
    }

    @Test
    void theButtonOpensTheCurrentViewsReadmeAgain() {
        var help = new Help(session, dialog);
        help.follow(new ReadmeView("Orders"));
        dialog.close();

        button(help).click();

        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Orders");
    }

    @Test
    void aViewWithoutAReadmeOpensNothingAndHidesHelp() {
        var help = new Help(session, dialog);

        help.follow(new Div());

        assertThat(dialog.isOpened()).isFalse();
        assertThat(help.isVisible()).isFalse();
    }

    @Test
    void helpReturnsWithTheNextViewThatHasAReadme() {
        var help = new Help(session, dialog);
        help.follow(new Div());

        help.follow(new ReadmeView("Orders"));

        assertThat(help.isVisible()).isTrue();
    }

    @Test
    void aReadmeStillOpenClosesWhenTheViewChanges() {
        var help = new Help(session, dialog);
        help.follow(new ReadmeView("Orders"));

        help.follow(new Div());

        assertThat(dialog.isOpened()).isFalse();
    }

    private static Button button(Help help) {
        return (Button)
                help.getChildren().filter(Button.class::isInstance).findFirst().orElseThrow();
    }
}
