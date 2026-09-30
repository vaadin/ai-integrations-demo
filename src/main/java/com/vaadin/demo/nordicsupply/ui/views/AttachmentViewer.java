package com.vaadin.demo.nordicsupply.ui.views;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.ai.common.ConfidenceLevel;
import com.vaadin.flow.component.ai.common.PageRegion;
import com.vaadin.flow.component.ai.common.Rect;
import com.vaadin.flow.component.ai.common.SourceExtract;
import com.vaadin.flow.component.ai.common.ValueSource;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A customer message as the AI read it, with the places it took a value from outlined: a PDF's pages or an image, or
 * a text. The form filler reports each place as a region of a page in fractions of the page's width and height, so
 * an outline is placed in percent over the page and lands on the text the model saw at whatever size the page is
 * shown. A PDF is shown as images of its pages, rendered here, as the model receives it too; its outlines go on the
 * text the model names, where the PDF prints it ({@link PdfText}), since the model's own rectangle is an estimate. A
 * text has no pages; the snippets the model reports having read are marked in it where they occur.
 */
class AttachmentViewer extends Div {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentViewer.class);

    /** The most pages of a PDF shown; a customer message is a page or two. */
    static final int MAX_PAGES = 10;

    /** Twice the PDF's own 72 dots per inch: sharp on a high-density screen at the width the column gives it. */
    private static final float DPI = 144;

    private static final String SOURCE = "source";

    private final List<Div> pages = new ArrayList<>();
    /** A PDF's text with where it is printed, to put an outline on the text it names; {@code null} for others. */
    private final PdfText printed;

    private final Pre text;
    private final String plain;
    private final String geometry;

    AttachmentViewer(String name, String mimeType, byte[] data) {
        addClassName("attachment");
        if (mimeType.startsWith("text/")) {
            plain = new String(data, StandardCharsets.UTF_8);
            text = new Pre(plain);
            text.addClassName("attachment-text");
            add(text);
            geometry = "";
            printed = null;
            return;
        }
        plain = null;
        text = null;
        var pdf = mimeType.equals("application/pdf");
        List<byte[]> images = List.of(data);
        String shape = "";
        PdfText layout = null;
        if (pdf) {
            images = List.of();
            try (var document = Loader.loadPDF(data)) {
                images = rendered(document);
                shape = geometry(document);
                layout = PdfText.of(document, MAX_PAGES);
            } catch (IOException e) {
                // the model may still read what cannot be shown here; the viewer then shows no pages
                LOG.warn("A PDF could not be rendered", e);
            }
        } else {
            shape = geometry(data);
        }
        geometry = shape;
        printed = layout;
        if (images.isEmpty()) {
            add(new Paragraph("This file cannot be shown here, but the AI reads it all the same."));
        }
        for (int i = 0; i < images.size(); i++) {
            var image = new Image(images.get(i), name + "-" + (i + 1), pdf ? "image/png" : mimeType);
            image.setAlt(images.size() == 1 ? name : "Page " + (i + 1) + " of " + name);
            var page = new Div(image);
            page.addClassName("attachment-page");
            pages.add(page);
            add(page);
        }
    }

    /**
     * What the model is told of the attachment's shape, to place the regions it reports: its pages and their size,
     * or an image's size in pixels; nothing for a text, or when the shape cannot be read.
     */
    String geometry() {
        return geometry;
    }

    /** How many pages the viewer shows; none for a text. */
    int pageCount() {
        return pages.size();
    }

    /**
     * Outlines where a value came from: each extract's region on its page, coloured by how sure the model was, or for
     * a text each snippet where it occurs. {@code null} clears the outlines. The first outline is scrolled into view.
     */
    void show(ValueSource source) {
        if (text != null) {
            mark(source);
            return;
        }
        pages.forEach(page -> page.getChildren()
                .filter(child -> child instanceof Div box && box.hasClassName(SOURCE))
                .toList()
                .forEach(page::remove));
        if (source == null) {
            return;
        }
        Div first = null;
        for (var extract : source.extracts()) {
            if (extract.location() instanceof PageRegion region
                    && region.page() >= 1
                    && region.page() <= pages.size()) {
                // on a PDF, where the text it names is printed; the model's own estimate where that is not found
                var places = printed == null
                        ? List.of(region.rect())
                        : printed.find(extract.text(), region.page(), region.rect())
                                .orElse(List.of(region.rect()));
                for (var place : places) {
                    var box = outline(extract, place, source.confidence());
                    pages.get(region.page() - 1).add(box);
                    first = first == null ? box : first;
                }
            }
        }
        if (first != null) {
            first.scrollIntoView(ScrollIntoViewOption.Behavior.SMOOTH, ScrollIntoViewOption.Block.NEAREST);
        }
    }

    private static Div outline(SourceExtract extract, Rect rect, ConfidenceLevel confidence) {
        var box = new Div();
        box.addClassNames(SOURCE, confidenceOf(confidence));
        box.setTitle(extract.text());
        box.getStyle()
                .set("left", percent(rect.x()))
                .set("top", percent(rect.y()))
                .set("width", percent(rect.width()))
                .set("height", percent(rect.height()));
        return box;
    }

    /** The text again, with each snippet marked where it first occurs, whitespace and case aside. */
    private void mark(ValueSource source) {
        text.removeAll();
        var marks = new ArrayList<int[]>();
        if (source != null) {
            for (var extract : source.extracts()) {
                var words = extract.text() == null
                        ? new String[0]
                        : extract.text().strip().split("\\s+");
                if (words.length == 0 || words[0].isEmpty()) {
                    continue;
                }
                var pattern = Pattern.compile(
                        Arrays.stream(words).map(Pattern::quote).collect(Collectors.joining("\\s+")),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                var found = pattern.matcher(plain);
                if (found.find() && marks.stream().noneMatch(m -> found.start() < m[1] && m[0] < found.end())) {
                    marks.add(new int[] {found.start(), found.end()});
                }
            }
        }
        marks.sort(Comparator.comparingInt(m -> m[0]));
        int from = 0;
        Span first = null;
        for (var m : marks) {
            text.add(new Text(plain.substring(from, m[0])));
            var marked = new Span(plain.substring(m[0], m[1]));
            marked.addClassNames(SOURCE, confidenceOf(source.confidence()));
            text.add(marked);
            first = first == null ? marked : first;
            from = m[1];
        }
        text.add(new Text(plain.substring(from)));
        if (first != null) {
            first.scrollIntoView(ScrollIntoViewOption.Behavior.SMOOTH, ScrollIntoViewOption.Block.NEAREST);
        }
    }

    /** A PDF's pages, up to {@link #MAX_PAGES}, as PNG images. */
    private static List<byte[]> rendered(PDDocument document) throws IOException {
        var renderer = new PDFRenderer(document);
        var images = new ArrayList<byte[]>();
        for (int i = 0; i < Math.min(document.getNumberOfPages(), MAX_PAGES); i++) {
            images.add(png(renderer.renderImageWithDPI(i, DPI, ImageType.RGB)));
        }
        return images;
    }

    private static byte[] png(BufferedImage image) {
        try {
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String geometry(PDDocument document) {
        var box = document.getPage(0).getMediaBox();
        var count = document.getNumberOfPages();
        return String.format(
                Locale.ROOT,
                "a PDF of %d page%s, each %.0f points wide and %.0f points tall",
                count,
                count == 1 ? "" : "s",
                box.getWidth(),
                box.getHeight());
    }

    /** An image's size in pixels; nothing for a format the JDK cannot read, such as WebP. */
    private static String geometry(byte[] image) {
        try {
            var read = ImageIO.read(new ByteArrayInputStream(image));
            return read == null
                    ? ""
                    : "an image " + read.getWidth() + " pixels wide and " + read.getHeight() + " pixels tall";
        } catch (IOException e) {
            LOG.debug("The size of an image could not be read", e);
            return "";
        }
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.2f%%", fraction * 100);
    }

    /** The outline's class for how sure the model was: {@code high}, {@code medium}, {@code low} or {@code unrated}. */
    private static String confidenceOf(ConfidenceLevel confidence) {
        return confidence == null ? "unrated" : confidence.name().toLowerCase(Locale.ROOT);
    }
}
