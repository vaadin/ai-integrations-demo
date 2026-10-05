package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ConfidenceLevel;
import com.vaadin.flow.component.ai.common.PageRegion;
import com.vaadin.flow.component.ai.common.Rect;
import com.vaadin.flow.component.ai.common.SourceExtract;
import com.vaadin.flow.component.ai.common.ValueSource;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.examples.PaintedExamples;

/**
 * A PDF is shown as its pages and an image as one, with the model told their shape; an outline goes on the text a
 * PDF prints, or where the model placed it on an image, coloured by confidence; a text marks the snippets in it.
 */
class AttachmentViewerTest {

    /** A page is an image the UI serves; held here, since the current UI is kept only weakly. */
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
    void aPdfIsShownAsItsPagesAndDescribedToTheModel() {
        var viewer = viewerOf("Late delivery.pdf");

        assertThat(pagesOf(viewer)).isEqualTo(1);
        assertThat(viewer.geometry()).isEqualTo("a PDF of 1 page, each 595 points wide and 842 points tall");
    }

    @Test
    void aPdfWithPagesOfDifferentSizesIsDescribedPageByPage() {
        var viewer = viewerOf("Damaged pallet.pdf");

        assertThat(pagesOf(viewer)).isEqualTo(2);
        assertThat(viewer.geometry())
                .matches("a PDF of 2 pages: page 1 595 points wide and \\d+ points tall;"
                        + " page 2 595 points wide and 842 points tall");
    }

    @Test
    void anImageIsOnePageOfItsSizeInPixels() {
        var viewer = viewerOf("Wrong colour.png");

        assertThat(pagesOf(viewer)).isEqualTo(1);
        assertThat(viewer.geometry()).matches("an image \\d+ pixels wide and \\d+ pixels tall");
    }

    @Test
    void anOutlineOnAPdfGoesOnTheTextItNamesNotOnTheEstimate() {
        var viewer = viewerOf("Damaged pallet.pdf");
        var farOff = new PageRegion(1, new Rect(0.05, 0.05, 0.2, 0.02));

        viewer.show(new ValueSource(
                ConfidenceLevel.HIGH, List.of(new SourceExtract("Kauppakatu 5, 96100 Rovaniemi", farOff))));

        assertThat(outlines(viewer)).singleElement().satisfies(box -> {
            assertThat(box.getClassNames()).contains("source", "high");
            assertThat(Double.parseDouble(box.getStyle().get("top").replace("%", "")))
                    .isGreaterThan(50);
        });
    }

    @Test
    void anOutlineOnAnImageIsWhereTheModelPlacedIt() {
        var viewer = viewerOf("Wrong colour.png");
        var region = new PageRegion(1, new Rect(0.1, 0.2, 0.3, 0.05));

        viewer.show(new ValueSource(null, List.of(new SourceExtract("SO-2026-006473", region))));

        assertThat(outlines(viewer)).singleElement().satisfies(box -> {
            assertThat(box.getClassNames()).contains("unrated");
            assertThat(box.getStyle().get("left")).isEqualTo("10.00%");
            assertThat(box.getStyle().get("top")).isEqualTo("20.00%");
            assertThat(box.getStyle().get("width")).isEqualTo("30.00%");
            assertThat(box.getStyle().get("height")).isEqualTo("5.00%");
        });
    }

    @Test
    void anOutlineSaysWhyTheValueRestsOnItAndTheReasoningIsNoOutline() {
        var viewer = viewerOf("Wrong colour.png");
        var region = new PageRegion(1, new Rect(0.1, 0.2, 0.3, 0.05));

        viewer.show(new ValueSource(
                ConfidenceLevel.HIGH,
                List.of(
                        new SourceExtract("SO-2026-006473", region),
                        new SourceExtract("Reasoning: The customer names the order.", region))));

        assertThat(outlines(viewer))
                .singleElement()
                .satisfies(box -> assertThat(((Component) box).getElement().getAttribute("title"))
                        .isEqualTo("The customer names the order."));
    }

    @Test
    void anExtractOnAPageTheAttachmentDoesNotHaveIsNotOutlined() {
        var viewer = viewerOf("Wrong colour.png");

        viewer.show(new ValueSource(
                ConfidenceLevel.LOW,
                List.of(
                        new SourceExtract("x", new PageRegion(2, new Rect(0, 0, 1, 1))),
                        new SourceExtract("y", null))));

        assertThat(outlines(viewer)).isEmpty();
    }

    @Test
    void noSourceClearsTheOutlines() {
        var viewer = viewerOf("Wrong colour.png");
        viewer.show(new ValueSource(
                ConfidenceLevel.HIGH, List.of(new SourceExtract("x", new PageRegion(1, new Rect(0, 0, 1, 1))))));

        viewer.show(null);

        assertThat(outlines(viewer)).isEmpty();
    }

    @Test
    void aTextMarksTheSnippetsItHas() {
        var message = "20 jackets came in Moss\nGreen, we ordered Midnight Blue.";
        var viewer = new AttachmentViewer(
                new AIAttachment("Pasted text", "text/plain", message.getBytes(StandardCharsets.UTF_8)));

        viewer.show(new ValueSource(
                ConfidenceLevel.MEDIUM,
                List.of(
                        new SourceExtract("moss green", null),
                        new SourceExtract("not in the text", null),
                        new SourceExtract("Reasoning: Green is the colour that came.", null))));

        assertThat(pagesOf(viewer)).isZero();
        assertThat(viewer.geometry()).isEmpty();
        assertThat(ComponentTree.descendants(viewer)
                        .filter(Span.class::isInstance)
                        .map(Span.class::cast))
                .singleElement()
                .satisfies(mark -> {
                    assertThat(mark.getText()).isEqualTo("Moss\nGreen");
                    assertThat(mark.getClassNames()).contains("source", "medium");
                });
    }

    private static AttachmentViewer viewerOf(String name) {
        return new AttachmentViewer(PaintedExamples.named(name).attachment());
    }

    /** The pages a viewer shows: none for a text. */
    private static long pagesOf(AttachmentViewer viewer) {
        return viewer.getChildren()
                .filter(child -> child instanceof Div page && page.hasClassName("attachment-page"))
                .count();
    }

    private static List<HasStyle> outlines(Component viewer) {
        return ComponentTree.descendants(viewer)
                .filter(c -> c instanceof HasStyle styled && styled.hasClassName("source"))
                .map(HasStyle.class::cast)
                .toList();
    }
}
