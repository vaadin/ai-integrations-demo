package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The dialog shows the readme it is given under the view's title and closes from its button. */
class ReadmeDialogTest {

    private static final String ORDERS = "The order list.";
    private static final String PRODUCTS = "The catalogue.";

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
    void showOpensTheDialogOnTheReadmesTitleAndText() {
        dialog.show("Orders", ORDERS);

        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Orders");
        assertThat(markdown().getContent()).isEqualTo(ORDERS);
    }

    @Test
    void showingAnotherReadmeReplacesTheTitleAndText() {
        dialog.show("Orders", ORDERS);

        dialog.show("Products", PRODUCTS);

        assertThat(dialog.getHeaderTitle()).isEqualTo("Products");
        assertThat(markdown().getContent()).isEqualTo(PRODUCTS);
    }

    @Test
    void theCloseButtonClosesTheDialog() {
        dialog.show("Orders", ORDERS);

        closeButton().click();

        assertThat(dialog.isOpened()).isFalse();
    }

    private Markdown markdown() {
        return (Markdown) dialog.getChildren()
                .filter(Markdown.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /** The footer's components sit in an element slotted as the footer. */
    private Button closeButton() {
        return (Button) dialog.getElement()
                .getChildren()
                .filter(element -> "footer".equals(element.getAttribute("slot")))
                .flatMap(Element::getChildren)
                .flatMap(element -> element.getComponent().stream())
                .filter(Button.class::isInstance)
                .findFirst()
                .orElseThrow();
    }
}
