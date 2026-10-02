package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.markdown.Markdown;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The popover shows the readme it is given under the view's title and closes from its button. */
class ReadmePopoverTest {

    private static final String ORDERS = "The order list.";
    private static final String PRODUCTS = "The catalogue.";

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
    void showOpensThePopoverOnTheReadmesTitleAndText() {
        popover.show("Orders", ORDERS);

        assertThat(popover.isOpened()).isTrue();
        assertThat(title().getText()).isEqualTo("Orders");
        assertThat(markdown().getContent()).isEqualTo(ORDERS);
    }

    @Test
    void showingAnotherReadmeReplacesTheTitleAndText() {
        popover.show("Orders", ORDERS);

        popover.show("Products", PRODUCTS);

        assertThat(title().getText()).isEqualTo("Products");
        assertThat(markdown().getContent()).isEqualTo(PRODUCTS);
    }

    @Test
    void theCloseButtonClosesThePopover() {
        popover.show("Orders", ORDERS);

        closeButton().click();

        assertThat(popover.isOpened()).isFalse();
    }

    private H2 title() {
        return (H2) popover.getChildren().filter(H2.class::isInstance).findFirst().orElseThrow();
    }

    private Markdown markdown() {
        return (Markdown)
                popover.getChildren().filter(Markdown.class::isInstance).findFirst().orElseThrow();
    }

    private Button closeButton() {
        return (Button) popover.getChildren().filter(Button.class::isInstance).findFirst().orElseThrow();
    }
}
