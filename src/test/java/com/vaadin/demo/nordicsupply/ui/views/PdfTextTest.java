package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.vaadin.flow.component.ai.common.Rect;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.examples.PaintedExamples;

/**
 * A snippet is found where the PDF prints it, whitespace and case aside, on the lines it runs over, at the occurrence
 * nearest the model's estimate; a snippet the page does not have is not found. The damaged pallet example has an
 * exported email as its first page and a scanned delivery note, written on by hand and stamped, as its second.
 */
class PdfTextTest {

    /** Anywhere on the page: no occurrence is preferred. */
    private static final Rect ANYWHERE = new Rect(0.5, 0.5, 0.01, 0.01);

    private static PdfText damagedPallet;

    @BeforeAll
    static void theDamagedPalletExample() throws IOException {
        var example = PaintedExamples.named("Damaged pallet.pdf");
        try (var document = Loader.loadPDF(example.attachment().data())) {
            damagedPallet = PdfText.of(document, AttachmentViewer.MAX_PAGES);
        }
    }

    @Test
    void aSnippetOnOneLineIsOneBoxWithinThePage() {
        var boxes = damagedPallet.find("KA9456355659", 2, ANYWHERE).orElseThrow();

        assertThat(boxes).singleElement().satisfies(box -> {
            assertThat(box.x()).isBetween(0.0, 0.5);
            assertThat(box.y()).isBetween(0.2, 0.4);
            assertThat(box.width()).isBetween(0.05, 0.4);
            assertThat(box.height()).isBetween(0.005, 0.05);
        });
    }

    @Test
    void aSnippetOverALineBreakIsABoxPerLine() {
        var boxes = damagedPallet
                .find("crushed and the stretch wrap torn open", 1, ANYWHERE)
                .orElseThrow();

        assertThat(boxes).hasSize(2);
        assertThat(boxes.get(0).y()).isLessThan(boxes.get(1).y());
    }

    @Test
    void handwritingAndStampsAreFoundAsWellAsPrint() {
        assertThat(damagedPallet.find("2 cartons wet - 6 jackets damaged", 2, ANYWHERE))
                .hasValueSatisfying(boxes -> assertThat(boxes).hasSize(1));
        assertThat(damagedPallet.find("RECEIVED WITH REMARKS", 2, ANYWHERE)).isPresent();
    }

    @Test
    void caseAndWhitespaceDoNotMatter() {
        assertThat(damagedPallet.find("top LAYER   crushed,\nstretch wrap torn", 2, ANYWHERE))
                .isPresent();
    }

    @Test
    void ofSeveralOccurrencesTheOneNearestTheEstimateIsTaken() {
        // the shipment is named in the subject, in the attachment's name and in the message
        var subject = new Rect(0.6, 0.05, 0.2, 0.02);
        var message = new Rect(0.2, 0.6, 0.2, 0.02);
        var top = damagedPallet.find("SH-2026-007291", 1, subject).orElseThrow();
        var bottom = damagedPallet.find("SH-2026-007291", 1, message).orElseThrow();

        assertThat(top.getFirst().y()).isLessThan(bottom.getFirst().y());
    }

    @Test
    void aSnippetThePageDoesNotHaveIsNotFound() {
        assertThat(damagedPallet.find("SO-2026-999999", 1, ANYWHERE)).isEmpty();
        assertThat(damagedPallet.find("RECEIVED WITH REMARKS", 1, ANYWHERE)).isEmpty();
        assertThat(damagedPallet.find("SO-2026-006830", 3, ANYWHERE)).isEmpty();
        assertThat(damagedPallet.find(" ", 1, ANYWHERE)).isEmpty();
    }
}
