package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.markdown.Markdown;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The popover shows the readme it is given, its additional sections, and the documentation the view links to.
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
    void showOpensThePopoverWithTheReadmesAccessibleTitleAndText() {
        popover.show("Orders", ORDERS, List.of(), List.of());

        assertThat(popover.isOpened()).isTrue();
        assertThat(popover.getAriaLabel()).contains("Orders help");
        assertThat(markdown().getContent()).isEqualTo(ORDERS);
    }

    @Test
    void showingAnotherReadmeReplacesTheTitleAndText() {
        popover.show("Orders", ORDERS, List.of(), List.of());

        popover.show("Products", PRODUCTS, List.of(), List.of());

        assertThat(popover.getAriaLabel()).contains("Products help");
        assertThat(markdown().getContent()).isEqualTo(PRODUCTS);
    }

    @Test
    void additionalReadmeSectionsAreAccordionPanels() {
        popover.show(
                "Claims",
                "Process claims.",
                List.of(
                        new HasReadme.ReadmeSection("Let AI Fill the Form", "Upload a document."),
                        new HasReadme.ReadmeSection("Confidence in the Results", "Review every field.")),
                List.of());

        assertThat(sectionPanels())
                .extracting(AccordionPanel::getSummaryText)
                .containsExactly("Let AI Fill the Form", "Confidence in the Results", "Read More");
        assertThat(sectionPanels().subList(0, 2))
                .flatExtracting(panel -> panel.getContent().toList())
                .extracting(Markdown.class::cast)
                .extracting(Markdown::getContent)
                .containsExactly("Upload a document.", "Review every field.");
    }

    /** The links open in a new tab, so the demo stays where it is; each one says what the page has for the view. */
    @Test
    void theLinksAreListedUnderReadMoreAsBlankTargetAnchorsWithTheirNotes() {
        popover.show(
                "Dashboard",
                "The dashboard.",
                List.of(),
                List.of(
                        new HasReadme.Link("AI-powered Grid", "https://vaadin.com/docs/grid", "the table widget"),
                        new HasReadme.Link("AI-powered Chart", "https://vaadin.com/docs/chart", "the chart widget")));

        assertThat(readMoreSection().isVisible()).isTrue();
        assertThat(readMoreItems())
                .extracting(item -> item.getElement().getTextRecursively())
                .containsExactly("AI-powered Gridthe table widget", "AI-powered Chartthe chart widget");
        assertThat(readMoreItems())
                .extracting(item -> (Anchor) item.getChildren().findFirst().orElseThrow())
                .extracting(Anchor::getHref, Anchor::getTarget)
                .containsExactly(
                        tuple("https://vaadin.com/docs/grid", Optional.of("_blank")),
                        tuple("https://vaadin.com/docs/chart", Optional.of("_blank")));
    }

    @Test
    void aReadmeWithoutLinksHasNoReadMoreAndAReadmeWithThemReplacesTheLast() {
        popover.show("Insights", "The dashboard.", List.of(), List.of(new HasReadme.Link("A", "https://a", "a")));

        popover.show("Orders", ORDERS, List.of(), List.of());

        assertThat(readMoreSection().isVisible()).isFalse();
        assertThat(readMoreItems()).isEmpty();
    }

    private AccordionPanel readMoreSection() {
        return descendants(popover)
                .filter(AccordionPanel.class::isInstance)
                .map(AccordionPanel.class::cast)
                .filter(panel -> panel.hasClassName("read-more"))
                .findFirst()
                .orElseThrow();
    }

    private List<AccordionPanel> sectionPanels() {
        return descendants(popover)
                .filter(AccordionPanel.class::isInstance)
                .map(AccordionPanel.class::cast)
                .toList();
    }

    private List<ListItem> readMoreItems() {
        return descendants(popover)
                .filter(UnorderedList.class::isInstance)
                .flatMap(Component::getChildren)
                .map(ListItem.class::cast)
                .toList();
    }

    private Markdown markdown() {
        return (Markdown) popover.getChildren()
                .filter(Markdown.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private java.util.stream.Stream<Component> descendants(Component root) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
