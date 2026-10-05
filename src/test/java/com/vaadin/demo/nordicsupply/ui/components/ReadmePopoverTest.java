package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.markdown.Markdown;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The popover shows the readme it is given under the view's title, with the documentation the view links to, and
 * closes from its button.
 */
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
        popover.show("Orders", ORDERS, List.of());

        assertThat(popover.isOpened()).isTrue();
        assertThat(title().getText()).isEqualTo("Orders");
        assertThat(markdown().getContent()).isEqualTo(ORDERS);
    }

    @Test
    void showingAnotherReadmeReplacesTheTitleAndText() {
        popover.show("Orders", ORDERS, List.of());

        popover.show("Products", PRODUCTS, List.of());

        assertThat(title().getText()).isEqualTo("Products");
        assertThat(markdown().getContent()).isEqualTo(PRODUCTS);
    }

    @Test
    void theCloseButtonClosesThePopover() {
        popover.show("Orders", ORDERS, List.of());

        closeButton().click();

        assertThat(popover.isOpened()).isFalse();
    }

    /** The links open in a new tab, so the demo stays where it is; each one says what the page has for the view. */
    @Test
    void theLinksAreListedUnderReadMoreAsBlankTargetAnchorsWithTheirNotes() {
        popover.show(
                "Insights",
                "The dashboard.",
                List.of(
                        new HasReadme.Link("AI-powered Grid", "https://vaadin.com/docs/grid", "the table widget"),
                        new HasReadme.Link("AI-powered Chart", "https://vaadin.com/docs/chart", "the chart widget")));

        assertThat(readMoreTitle().isVisible()).isTrue();
        assertThat(readMoreItems())
                .extracting(item -> item.getElement().getTextRecursively())
                .containsExactly("AI-powered Grid: the table widget", "AI-powered Chart: the chart widget");
        assertThat(readMoreItems())
                .extracting(item -> (Anchor) item.getChildren().findFirst().orElseThrow())
                .extracting(Anchor::getHref, Anchor::getTarget)
                .containsExactly(
                        tuple("https://vaadin.com/docs/grid", Optional.of("_blank")),
                        tuple("https://vaadin.com/docs/chart", Optional.of("_blank")));
    }

    @Test
    void aReadmeWithoutLinksHasNoReadMoreAndAReadmeWithThemReplacesTheLast() {
        popover.show("Insights", "The dashboard.", List.of(new HasReadme.Link("A", "https://a", "a")));

        popover.show("Orders", ORDERS, List.of());

        assertThat(readMoreTitle().isVisible()).isFalse();
        assertThat(readMoreItems()).isEmpty();
    }

    private H3 readMoreTitle() {
        return (H3)
                popover.getChildren().filter(H3.class::isInstance).findFirst().orElseThrow();
    }

    private List<ListItem> readMoreItems() {
        return popover.getChildren()
                .filter(UnorderedList.class::isInstance)
                .flatMap(Component::getChildren)
                .map(ListItem.class::cast)
                .toList();
    }

    private H2 title() {
        return (H2)
                popover.getChildren().filter(H2.class::isInstance).findFirst().orElseThrow();
    }

    private Markdown markdown() {
        return (Markdown) popover.getChildren()
                .filter(Markdown.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private Button closeButton() {
        return (Button) popover.getChildren()
                .filter(Button.class::isInstance)
                .findFirst()
                .orElseThrow();
    }
}
