package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The dialog shows the readme it is given and closes from its button. */
class ReadmeDialogTest {

    private static final Readme ORDERS = new Readme("Orders", "The order list.");
    private static final Readme PRODUCTS = new Readme("Products", "The catalogue.");

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
        dialog.show(ORDERS);

        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Orders");
        assertThat(markdown().getContent()).isEqualTo(ORDERS.body());
    }

    @Test
    void showingAnotherReadmeReplacesTheTitleAndText() {
        dialog.show(ORDERS);

        dialog.show(PRODUCTS);

        assertThat(dialog.getHeaderTitle()).isEqualTo("Products");
        assertThat(markdown().getContent()).isEqualTo(PRODUCTS.body());
    }

    @Test
    void theCloseButtonClosesTheDialog() {
        dialog.show(ORDERS);

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
