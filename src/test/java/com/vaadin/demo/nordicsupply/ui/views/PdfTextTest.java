package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.vaadin.flow.component.ai.common.Rect;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A snippet is found where the PDF prints it, whitespace and case aside, on the lines it runs over, at the occurrence
 * nearest the model's estimate; a snippet the page does not have is not found.
 */
class PdfTextTest {

    /** Anywhere on the page: no occurrence is preferred. */
    private static final Rect ANYWHERE = new Rect(0.5, 0.5, 0.01, 0.01);

    private static PdfText damagedPallet;

    @BeforeAll
    static void theDamagedPalletEmail() throws IOException {
        try (var document = Loader.loadPDF(ExampleFile.DAMAGED_PALLET.bytes())) {
            damagedPallet = PdfText.of(document, AttachmentViewer.MAX_PAGES);
        }
    }

    @Test
    void theTextIsThePagesWithWhitespaceCollapsed() {
        assertThat(damagedPallet.text(1))
                .contains("Order number: SO-2026-007365 Customer: Retkivakka Tampere (C-10001)")
                .doesNotContain("  ");
    }

    @Test
    void aSnippetOnOneLineIsOneBoxWithinThePage() {
        var boxes =
                damagedPallet.find("Order number: SO-2026-007365", 1, ANYWHERE).orElseThrow();

        assertThat(boxes).singleElement().satisfies(box -> {
            assertThat(box.x()).isBetween(0.0, 0.5);
            assertThat(box.y()).isBetween(0.5, 1.0);
            assertThat(box.width()).isBetween(0.1, 0.6);
            assertThat(box.height()).isBetween(0.005, 0.05);
        });
    }

    @Test
    void aSnippetOverALineBreakIsABoxPerLine() {
        var boxes = damagedPallet
                .find("the outer wrap was torn and the cartons at the bottom were crushed", 1, ANYWHERE)
                .orElseThrow();

        assertThat(boxes).hasSize(2);
        assertThat(boxes.get(0).y()).isLessThan(boxes.get(1).y());
    }

    @Test
    void caseAndWhitespaceDoNotMatter() {
        assertThat(damagedPallet.find("ORDER   NUMBER:\nso-2026-007365", 1, ANYWHERE))
                .isPresent();
    }

    @Test
    void ofSeveralOccurrencesTheOneNearestTheEstimateIsTaken() {
        var top = damagedPallet
                .find("Retkivakka Tampere", 1, new Rect(0.2, 0.0, 0.3, 0.02))
                .orElseThrow();
        var bottom = damagedPallet
                .find("Retkivakka Tampere", 1, new Rect(0.5, 0.9, 0.3, 0.02))
                .orElseThrow();

        assertThat(top.getFirst().y()).isLessThan(bottom.getFirst().y());
    }

    @Test
    void aSnippetThePageDoesNotHaveIsNotFound() {
        assertThat(damagedPallet.find("SO-2026-999999", 1, ANYWHERE)).isEmpty();
        assertThat(damagedPallet.find("SO-2026-007365", 2, ANYWHERE)).isEmpty();
        assertThat(damagedPallet.find(" ", 1, ANYWHERE)).isEmpty();
    }
}
