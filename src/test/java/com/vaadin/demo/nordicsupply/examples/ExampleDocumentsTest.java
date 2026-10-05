package com.vaadin.demo.nordicsupply.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.util.Images;

/**
 * The example documents are painted at start-up: six files, one per country, each a readable PDF or image of the
 * kind its tile says, and a region's account is offered the ones of its own countries.
 */
class ExampleDocumentsTest {

    private static final ExampleDocuments documents = PaintedExamples.ALL;

    @Test
    void thereIsOneExamplePerCountryInTheOrderTheyAreShown() {
        assertThat(documents.all())
                .extracting(ExampleDocument::name, ExampleDocument::country)
                .containsExactly(
                        tuple("Damaged pallet.pdf", "FI"),
                        tuple("Missing cartons.png", "SE"),
                        tuple("Late delivery.pdf", "DE"),
                        tuple("Quality defect.jpg", "NO"),
                        tuple("Pricing dispute.pdf", "DK"),
                        tuple("Wrong colour.png", "EE"));
    }

    @Test
    void thePdfsHaveThePagesTheirTilesPromise() throws IOException {
        assertThat(pagesOf("Damaged pallet.pdf")).isEqualTo(2);
        assertThat(pagesOf("Late delivery.pdf")).isEqualTo(1);
        assertThat(pagesOf("Pricing dispute.pdf")).isEqualTo(1);
        assertThat(documents.all()).filteredOn(ExampleDocument::isPdf).hasSize(3);
    }

    /** A PDF carries its text under the page image, as an OCR'd scan does: print, handwriting and stamps alike. */
    @Test
    void thePdfsCarryTheTextTheirPagesShow() throws IOException {
        assertThat(text("Damaged pallet.pdf"))
                .contains("SO-2026-006830", "Top layer crushed, stretch wrap torn.", "RECEIVED WITH REMARKS");
        assertThat(text("Late delivery.pdf")).contains("SH-2026-006877", "359.27 EUR");
        assertThat(text("Pricing dispute.pdf")).contains("Difference 15.98 x 48 = 767.04 EUR - please credit.");
    }

    /** Text painted below a page's end would be in the PDF's text but not on its image. */
    @Test
    void thePdfsTextLiesOnTheirPages() throws IOException {
        for (var name : List.of("Damaged pallet.pdf", "Late delivery.pdf", "Pricing dispute.pdf")) {
            try (var document = Loader.loadPDF(example(name).attachment().data())) {
                var offPage = new ArrayList<String>();
                var stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> positions) {
                        var first = positions.getFirst();
                        if (first.getYDirAdj() > first.getPageHeight() || first.getXDirAdj() > first.getPageWidth()) {
                            offPage.add(text);
                        }
                    }
                };
                stripper.writeText(document, Writer.nullWriter());
                assertThat(offPage).as(name).isEmpty();
            }
        }
    }

    @Test
    void theImagesAreReadableAndSayWhatTheyAre() {
        var screenshot = example("Missing cartons.png");
        var photo = example("Quality defect.jpg");

        assertThat(screenshot.attachment().mimeType()).isEqualTo("image/png");
        assertThat(Images.size(screenshot.attachment().data()))
                .hasValueSatisfying(size -> assertThat(size.width).isEqualTo(1400));
        assertThat(photo.attachment().mimeType()).isEqualTo("image/jpeg");
        assertThat(Images.size(photo.attachment().data()))
                .hasValueSatisfying(size -> assertThat(size.width).isGreaterThan(1200));
        assertThat(photo.isPdf()).isFalse();
    }

    /** Every file is small enough to send to a model with the prompt, and not empty. */
    @Test
    void everyFileIsOfASizeToSend() {
        for (var document : documents.all()) {
            assertThat(document.attachment().data().length).as(document.name()).isBetween(10_000, 2_000_000);
        }
    }

    @Test
    void aRegionIsOfferedItsOwnCountriesExamplesAndTheHeadOfficeAllOfThem() {
        assertThat(documents.forCountries(Scopes.FI_EE.countries()))
                .extracting(ExampleDocument::name)
                .containsExactly("Damaged pallet.pdf", "Wrong colour.png");
        assertThat(documents.forCountries(Scopes.DK_DE.countries()))
                .extracting(ExampleDocument::name)
                .containsExactly("Late delivery.pdf", "Pricing dispute.pdf");
        assertThat(documents.forCountries(Scopes.ALL.countries())).hasSize(6);
        assertThat(documents.forCountries(List.of())).isEmpty();
    }

    /** The same page every time: the randomness of the handwriting and the signatures is seeded. */
    @Test
    void theDocumentsArePaintedTheSameEveryTime() {
        var again = new ExampleDocuments();

        for (int i = 0; i < documents.all().size(); i++) {
            assertThat(again.all().get(i).attachment().data())
                    .as(documents.all().get(i).name())
                    .isEqualTo(documents.all().get(i).attachment().data());
        }
    }

    private static int pagesOf(String name) throws IOException {
        try (var document = Loader.loadPDF(example(name).attachment().data())) {
            return document.getNumberOfPages();
        }
    }

    private static String text(String name) throws IOException {
        try (var document = Loader.loadPDF(example(name).attachment().data())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static ExampleDocument example(String name) {
        return PaintedExamples.named(name);
    }
}
