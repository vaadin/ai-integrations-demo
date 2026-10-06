package com.vaadin.demo.nordicsupply.ui.views;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;

import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ConfidenceLevel;
import com.vaadin.flow.component.ai.common.PageRegion;
import com.vaadin.flow.component.ai.common.Rect;
import com.vaadin.flow.component.ai.common.ValueSource;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.demo.nordicsupply.util.Images;
import com.vaadin.demo.nordicsupply.util.Pdfs;

/**
 * A customer message as the AI read it, with the places it took a value from outlined: a PDF's pages or an image, or
 * a text. The form filler reports each place as a region of a page in fractions of the page's width and height, so
 * an outline is placed in percent over the page and lands on the text the model saw at whatever size the page is
 * shown. A PDF is shown as images of its pages, rendered as the browser asks for them, as the model receives it too;
 * its outlines go on the
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

    /** The source outlined now, or {@code null}. */
    private ValueSource shown;

    AttachmentViewer(AIAttachment attachment) {
        addClassName("attachment");
        var name = attachment.name();
        var mimeType = attachment.mimeType();
        if (mimeType.startsWith("text/")) {
            plain = new String(attachment.data(), StandardCharsets.UTF_8);
            text = new Pre(plain);
            text.addClassName("attachment-text");
            add(text);
            geometry = "";
            printed = null;
            return;
        }
        plain = null;
        text = null;
        if (Pdfs.MIME_TYPE.equals(mimeType)) {
            var count = 0;
            var shape = "";
            PdfText layout = null;
            try (var document = Loader.loadPDF(attachment.data())) {
                count = Math.min(document.getNumberOfPages(), MAX_PAGES);
                shape = geometry(document);
                layout = PdfText.of(document, MAX_PAGES);
            } catch (IOException e) {
                // the view reads a PDF before it shows it here, so this is not expected; the viewer then shows no pages
                LOG.warn("A PDF could not be read", e);
            }
            geometry = shape;
            printed = layout;
            for (int i = 0; i < count; i++) {
                addPage(new Image(rendered(attachment, i), count == 1 ? name : "Page " + (i + 1) + " of " + name));
            }
        } else {
            geometry = Images.size(attachment.data())
                    .map(size -> "an image " + size.width + " pixels wide and " + size.height + " pixels tall")
                    .orElse("");
            printed = null;
            var image = new Image(attachment.data(), name, mimeType);
            image.setAlt(name);
            addPage(image);
        }
        if (pages.isEmpty()) {
            add(new Paragraph("This file cannot be shown here, but the AI reads it all the same."));
        }
    }

    private void addPage(Image image) {
        var page = new Div(image);
        page.addClassName("attachment-page");
        pages.add(page);
        add(page);
    }

    /**
     * What the model is told of the attachment's shape, to place the regions it reports: its pages and their size,
     * or an image's size in pixels; nothing for a text, or when the shape cannot be read.
     */
    String geometry() {
        return geometry;
    }

    /**
     * Outlines where a value came from: each snippet's region on its page, coloured by how sure the model was, or for
     * a text each snippet where it occurs; the model's reasoning is no place in the message, and is left out.
     * {@code null} clears the outlines. The first outline is scrolled into view.
     */
    void show(ValueSource source) {
        // focus moving between fields the AI did not fill shows no source again and again
        if (Objects.equals(source, shown)) {
            return;
        }
        shown = source;
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
        // an outline's tooltip says why the value rests on it, which the outlined words themselves do not
        var reasoning = ValueSources.reasoning(source);
        for (var extract : ValueSources.snippets(source)) {
            if (extract.location() instanceof PageRegion region
                    && region.page() >= 1
                    && region.page() <= pages.size()) {
                // on a PDF, where the text it names is printed; the model's own estimate where that is not found
                var places = printed == null
                        ? List.of(region.rect())
                        : printed.find(extract.text(), region.page(), region.rect())
                                .orElse(List.of(region.rect()));
                for (var place : places) {
                    var box = outline(reasoning.orElse(extract.text()), place, source.confidence());
                    pages.get(region.page() - 1).add(box);
                    first = first == null ? box : first;
                }
            }
        }
        if (first != null) {
            first.scrollIntoView(ScrollIntoViewOption.Behavior.SMOOTH, ScrollIntoViewOption.Block.NEAREST);
        }
    }

    private static Div outline(String title, Rect rect, ConfidenceLevel confidence) {
        var box = new Div();
        box.addClassNames(SOURCE, confidenceOf(confidence));
        box.setTitle(title);
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
            for (var extract : ValueSources.snippets(source)) {
                ValueSources.pattern(extract.text())
                        .map(pattern -> pattern.matcher(plain))
                        .filter(Matcher::find)
                        .map(found -> new int[] {found.start(), found.end()})
                        .filter(found -> marks.stream().noneMatch(m -> found[0] < m[1] && m[0] < found[1]))
                        .ifPresent(marks::add);
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

    /**
     * A PDF page as a PNG image, rendered when the browser asks for it: outside the session's lock, and with nothing
     * kept but the PDF.
     */
    private static DownloadHandler rendered(AIAttachment pdf, int page) {
        return DownloadHandler.fromInputStream(event -> {
            try (var document = Loader.loadPDF(pdf.data())) {
                var png = Images.png(new PDFRenderer(document).renderImageWithDPI(page, DPI, ImageType.RGB));
                return new DownloadResponse(
                        new ByteArrayInputStream(png), pdf.name() + "-" + (page + 1) + ".png", "image/png", png.length);
            }
        });
    }

    /** The pages and their size in points: one size for them all when they share it, each page's otherwise. */
    private static String geometry(PDDocument document) {
        var count = document.getNumberOfPages();
        var sizes = new ArrayList<String>();
        for (var page : document.getPages()) {
            var box = page.getMediaBox();
            sizes.add(String.format(
                    Locale.ROOT, "%.0f points wide and %.0f points tall", box.getWidth(), box.getHeight()));
        }
        if (sizes.stream().distinct().count() == 1) {
            return "a PDF of " + count + " page" + (count == 1 ? "" : "s") + ", each " + sizes.getFirst();
        }
        var pages = new ArrayList<String>();
        for (int i = 0; i < sizes.size(); i++) {
            pages.add("page " + (i + 1) + " " + sizes.get(i));
        }
        return "a PDF of " + count + " pages: " + String.join("; ", pages);
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.2f%%", fraction * 100);
    }

    /** The outline's class for how sure the model was: {@code high}, {@code medium}, {@code low} or {@code unrated}. */
    private static String confidenceOf(ConfidenceLevel confidence) {
        return confidence == null ? "unrated" : confidence.name().toLowerCase(Locale.ROOT);
    }
}
