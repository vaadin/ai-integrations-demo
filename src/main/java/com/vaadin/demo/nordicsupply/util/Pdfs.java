package com.vaadin.demo.nordicsupply.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;

/**
 * PDFs made of page images, as a scanner writes them: one page per image, with the text on the page laid over it
 * invisibly, the way a scan that went through OCR carries what it read. A reader sees the image; a program that reads
 * the PDF's text finds the words where the image shows them.
 */
public final class Pdfs {

    /**
     * A run of text on a page image, in the image's pixels: where it starts on its baseline, how wide it is painted,
     * its font size, and how far it is turned clockwise about its start, in degrees.
     */
    public record Run(String text, double x, double y, double size, double width, double angle) {}

    /** A page: its image, and the text painted on it. */
    public record Page(BufferedImage image, List<Run> text) {}

    /** PDF points per inch; a page's size in points is its pixels at the rendering resolution, scaled by this. */
    private static final float POINTS_PER_INCH = 72f;

    /** The JPEG quality of a page bound into a PDF: a scan's, small enough to send to a model. */
    private static final float PAGE_JPEG_QUALITY = 0.86f;

    /** The seed of a written PDF's trailer ID, the same for every document, so that the bytes are reproducible. */
    private static final long DOCUMENT_ID = 20260907L;

    private Pdfs() {}

    /**
     * Binds page images into a PDF, one page per image, each page the size of its image at the given resolution,
     * with its text laid over it invisibly. The text is set in Helvetica, stretched to the width it is painted at and
     * turned as it is painted, so that its words lie over their picture; a character Helvetica cannot write becomes
     * a question mark.
     *
     * @throws UncheckedIOException when PDFBox cannot write the document
     */
    public static byte[] fromPages(List<Page> pages, float dpi) {
        try (var document = new PDDocument()) {
            // the trailer's ID is otherwise derived from the clock; pinned, the same pages give the same bytes
            document.setDocumentId(DOCUMENT_ID);
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var scale = POINTS_PER_INCH / dpi;
            for (var page : pages) {
                var width = page.image().getWidth() * scale;
                var height = page.image().getHeight() * scale;
                var sheet = new PDPage(new PDRectangle(width, height));
                document.addPage(sheet);
                var content = JPEGFactory.createFromImage(document, page.image(), PAGE_JPEG_QUALITY);
                try (var stream = new PDPageContentStream(document, sheet)) {
                    stream.drawImage(content, 0, 0, width, height);
                    stream.beginText();
                    stream.setRenderingMode(RenderingMode.NEITHER);
                    for (var run : page.text()) {
                        write(stream, font, run, scale, height);
                    }
                    stream.endText();
                }
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the PDF", e);
        }
    }

    /**
     * How many pages a PDF has.
     *
     * @throws IOException when the bytes are not a PDF PDFBox can read
     */
    public static int pageCount(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    /** One run of the invisible text: at its start, turned as painted, as wide as painted. */
    private static void write(PDPageContentStream stream, PDFont font, Run run, float scale, float pageHeight)
            throws IOException {
        var text = writable(font, run.text());
        var size = (float) (run.size() * scale);
        var natural = font.getStringWidth(text) / 1000 * size;
        if (natural <= 0) {
            return;
        }
        stream.setFont(font, size);
        stream.setHorizontalScaling((float) (run.width() * scale / natural * 100));
        // the image's y grows downwards and its turns are clockwise; the PDF's y grows upwards, so its turn is the
        // opposite
        stream.setTextMatrix(Matrix.getRotateInstance(
                Math.toRadians(-run.angle()), (float) (run.x() * scale), (float) (pageHeight - run.y() * scale)));
        stream.showText(text);
    }

    /** The text with every character the font cannot encode replaced by a question mark. */
    private static String writable(PDFont font, String text) {
        var out = new StringBuilder();
        text.codePoints().forEach(point -> {
            var character = Character.toString(point);
            try {
                font.encode(character);
                out.append(character);
            } catch (IOException | IllegalArgumentException e) {
                out.append('?');
            }
        });
        return out.toString();
    }
}
