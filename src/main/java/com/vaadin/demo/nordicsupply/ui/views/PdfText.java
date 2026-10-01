package com.vaadin.demo.nordicsupply.ui.views;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.vaadin.flow.component.ai.common.Rect;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * The text of a PDF's pages with where each character is, to outline a snippet exactly where it is printed. The
 * form filler reports where it read a value as a rectangle it estimates from the page's image, which on a page of
 * small print is often a line off; the snippet it reports is the text it read, so the outline is placed on that text
 * instead, and the estimate only picks among the places where the text occurs. Rectangles are in fractions of the
 * page, as the model's are.
 */
final class PdfText implements Serializable {

    /** A little room around the glyphs, in points, so that an outline does not touch the text. */
    private static final float PADDING = 1.5f;

    /** One page: its text, whitespace collapsed to single spaces, and each character's box, {@code null} for a space. */
    private record Page(String text, List<Rect> boxes) implements Serializable {}

    private final List<Page> pages;

    private PdfText(List<Page> pages) {
        this.pages = pages;
    }

    /** The text of the document's first {@code maxPages} pages. */
    static PdfText of(PDDocument document, int maxPages) throws IOException {
        var stripper = new Stripper();
        stripper.setSortByPosition(true);
        stripper.setEndPage(maxPages);
        stripper.writeText(document, Writer.nullWriter());
        return new PdfText(stripper.pages.stream()
                .map(page -> new Page(page.text.toString(), Collections.unmodifiableList(page.boxes)))
                .toList());
    }

    /**
     * Where the snippet is printed on the page, a box per line it runs over, at the occurrence nearest the estimate;
     * empty when the page does not have the snippet.
     *
     * @param page 1-based, as the form filler counts
     * @param estimate the model's rectangle, which picks among several occurrences
     * @return the snippet's boxes, in fractions of the page, top to bottom
     */
    Optional<List<Rect>> find(String snippet, int page, Rect estimate) {
        if (snippet == null || page < 1 || page > pages.size()) {
            return Optional.empty();
        }
        var needle = collapse(snippet).toLowerCase(Locale.ROOT);
        var on = pages.get(page - 1);
        var haystack = on.text().toLowerCase(Locale.ROOT);
        if (needle.isEmpty() || haystack.length() != on.text().length()) {
            return Optional.empty();
        }
        List<Rect> nearest = null;
        double best = Double.MAX_VALUE;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            var lines = lines(on.boxes().subList(at, at + needle.length()));
            if (lines.isEmpty()) {
                continue;
            }
            var distance = distance(lines.getFirst(), estimate);
            if (distance < best) {
                best = distance;
                nearest = lines;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** The boxes of a run of characters joined into one per line: characters on a line share their baseline. */
    private static List<Rect> lines(List<Rect> characters) {
        var lines = new ArrayList<Rect>();
        for (var box : characters) {
            if (box == null) {
                continue;
            }
            var last = lines.isEmpty() ? null : lines.getLast();
            if (last != null && Math.abs(last.y() - box.y()) < box.height() / 2) {
                var left = Math.min(last.x(), box.x());
                var right = Math.max(last.x() + last.width(), box.x() + box.width());
                var top = Math.min(last.y(), box.y());
                var bottom = Math.max(last.y() + last.height(), box.y() + box.height());
                lines.set(lines.size() - 1, new Rect(left, top, right - left, bottom - top));
            } else {
                lines.add(box);
            }
        }
        lines.sort(Comparator.comparingDouble(Rect::y));
        return lines;
    }

    private static double distance(Rect a, Rect b) {
        if (b == null) {
            return 0;
        }
        var dx = (a.x() + a.width() / 2) - (b.x() + b.width() / 2);
        var dy = (a.y() + a.height() / 2) - (b.y() + b.height() / 2);
        return dx * dx + dy * dy;
    }

    private static String collapse(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    /** Collects each page's characters and their boxes as the text is laid out, spaces collapsed. */
    private static final class Stripper extends PDFTextStripper {

        private static final class Collected {
            final StringBuilder text = new StringBuilder();
            final List<Rect> boxes = new ArrayList<>();
        }

        final List<Collected> pages = new ArrayList<>();

        Stripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pages.add(new Collected());
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            var page = pages.getLast();
            for (var position : positions) {
                var unicode = position.getUnicode();
                if (unicode == null || unicode.isBlank()) {
                    space(page);
                    continue;
                }
                // a ligature is one position for several characters; each of them gets its box
                var box = box(position);
                for (int i = 0; i < unicode.length(); i++) {
                    page.text.append(unicode.charAt(i));
                    page.boxes.add(box);
                }
            }
        }

        @Override
        protected void writeWordSeparator() {
            space(pages.getLast());
        }

        @Override
        protected void writeLineSeparator() {
            space(pages.getLast());
        }

        private static void space(Collected page) {
            if (!page.text.isEmpty() && page.text.charAt(page.text.length() - 1) != ' ') {
                page.text.append(' ');
                page.boxes.add(null);
            }
        }

        /**
         * A glyph's box from its baseline and font size, in fractions of the page, top-left origin, cut to the page;
         * {@code null} for a glyph that lies off it.
         */
        private static Rect box(TextPosition position) {
            var width = position.getPageWidth();
            var height = position.getPageHeight();
            var size = position.getFontSizeInPt();
            // kept on the page, which a rectangle has to be
            var left = clamp(position.getXDirAdj() - PADDING, width);
            var right = clamp(position.getXDirAdj() + position.getWidthDirAdj() + PADDING, width);
            var top = clamp(position.getYDirAdj() - size * 0.8f - PADDING, height);
            var bottom = clamp(position.getYDirAdj() + size * 0.25f + PADDING, height);
            if (right <= left || bottom <= top) {
                return null;
            }
            return new Rect(left / width, top / height, (right - left) / width, (bottom - top) / height);
        }

        private static float clamp(float value, float limit) {
            return Math.clamp(value, 0, limit);
        }
    }

    /** The text of a page as it is searched: whitespace collapsed to single spaces. */
    String text(int page) {
        return pages.get(page - 1).text();
    }
}
