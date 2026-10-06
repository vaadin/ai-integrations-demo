package com.vaadin.demo.nordicsupply.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/** A PDF is cut to its first pages when it has more than the cap, and left as it is otherwise. */
class PdfsTest {

    private static byte[] blankPages(int count) throws IOException {
        try (var document = new PDDocument()) {
            for (int i = 0; i < count; i++) {
                document.addPage(new PDPage());
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static int pagesOf(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void aPdfWithinTheCapIsTheSameBytes() throws IOException {
        var pdf = blankPages(10);

        assertThat(Pdfs.firstPages(pdf, 10)).isSameAs(pdf);
    }

    @Test
    void aLongerPdfIsCutToItsFirstPages() throws IOException {
        var cut = Pdfs.firstPages(blankPages(12), 10);

        assertThat(pagesOf(cut)).isEqualTo(10);
    }

    @Test
    void whatIsNotAPdfCannotBeCut() {
        var notAPdf = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Pdfs.firstPages(notAPdf, 10)).isInstanceOf(UncheckedIOException.class);
    }
}
