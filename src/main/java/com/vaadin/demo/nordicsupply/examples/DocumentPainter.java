package com.vaadin.demo.nordicsupply.examples;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.vaadin.demo.nordicsupply.util.Pdfs;

/**
 * Paints one page of an example document with Java2D: lines of text, paragraphs that wrap, rules and boxes,
 * handwriting, a stamp, a signature, and the effects that make a page look scanned or photographed. The pages are
 * bitmaps painted at start-up, so the repository holds no binary documents and what each one says can be read in
 * {@link ExampleDocuments}. Everything random, the jitter of the handwriting and the strokes of a signature, comes
 * from a seeded generator, so the same page is painted every time.
 * <p>
 * Every run of text painted is also recorded where it lies on the page, so that a PDF of the page carries it as its
 * text, the way a scan that went through OCR does ({@link Pdfs#fromPages}); the claim view outlines a value's source
 * on that text.
 */
final class DocumentPainter {

    /** The resolution the pages are painted at: an A4 page at 150 dpi, a scan's. */
    static final float DPI = 150f;

    static final int A4_WIDTH = 1240;
    static final int A4_HEIGHT = 1754;

    static final Color INK = new Color(0x20, 0x20, 0x20);
    static final Color GREY = new Color(0x6b, 0x6b, 0x6b);
    static final Color RULE = new Color(0xcf, 0xcf, 0xcf);
    static final Color PAPER = Color.WHITE;
    /** A blue ballpoint pen. */
    static final Color PEN = new Color(0x1f, 0x3a, 0x93);
    /** A red ink stamp. */
    static final Color STAMP = new Color(0xb7, 0x1c, 0x1c);
    /** A yellow highlighter. */
    static final Color HIGHLIGHTER = new Color(0xff, 0xee, 0x58, 0x90);

    private static final float LINE_SPACING = 1.45f;
    private static final float HANDWRITING_TILT_DEGREES = -1.2f;
    /** How askew a scanned page lies on the platen. */
    private static final double SCAN_TILT_DEGREES = 0.7;

    private final BufferedImage image;
    private final Graphics2D g;
    private final Random random;
    private final List<Pdfs.Run> runs = new ArrayList<>();

    /**
     * A blank page of the given size, filled with the background.
     *
     * @param seed what the randomness of the page starts from; the same seed paints the same page
     */
    DocumentPainter(int width, int height, Color background, long seed) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        random = new Random(seed);
    }

    /** An A4 page of white paper. */
    static DocumentPainter a4(long seed) {
        return new DocumentPainter(A4_WIDTH, A4_HEIGHT, PAPER, seed);
    }

    static Font sans(int size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    static Font sansBold(int size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    static Font serif(int size) {
        return new Font(Font.SERIF, Font.PLAIN, size);
    }

    static Font serifBold(int size) {
        return new Font(Font.SERIF, Font.BOLD, size);
    }

    static Font mono(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    int width() {
        return image.getWidth();
    }

    int height() {
        return image.getHeight();
    }

    /** The width of a text in a font, to place what follows it or to right-align it. */
    int widthOf(String text, Font font) {
        return g.getFontMetrics(font).stringWidth(text);
    }

    /** One line of text, its baseline at {@code y}. */
    DocumentPainter text(String text, int x, int y, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, y);
        record(text, x, y, font.getSize2D(), widthOf(text, font), 0);
        return this;
    }

    /** One line of text ending at {@code right}. */
    DocumentPainter textRight(String text, int right, int y, Font font, Color color) {
        return text(text, right - widthOf(text, font), y, font, color);
    }

    /**
     * A paragraph wrapped to the width, a line break in the text starting a new line. The first line's baseline is
     * at {@code y}.
     *
     * @return the baseline of the line that would follow the paragraph, to continue from
     */
    int paragraph(String text, int x, int y, int width, Font font, Color color) {
        int baseline = y;
        for (var line : wrap(text, width, font)) {
            text(line, x, baseline, font, color);
            baseline += lineHeight(font);
        }
        return baseline;
    }

    /** The lines a paragraph wraps to at the width, a line break in the text starting a new one. */
    List<String> wrap(String text, int width, Font font) {
        var metrics = g.getFontMetrics(font);
        var lines = new ArrayList<String>();
        for (var block : text.split("\n")) {
            var line = new StringBuilder();
            for (var word : block.split(" ")) {
                var candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** The height of one line of a font, to space what is laid out by hand like the paragraphs. */
    static int lineHeight(Font font) {
        return Math.round(font.getSize2D() * LINE_SPACING);
    }

    DocumentPainter rule(int x1, int y1, int x2, int y2, Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(new BasicStroke(1));
        return this;
    }

    /** A box, filled and bordered when the colours are given, with rounded corners when {@code radius} is above 0. */
    DocumentPainter box(int x, int y, int width, int height, Color fill, Color border, int radius) {
        if (fill != null) {
            g.setColor(fill);
            g.fillRoundRect(x, y, width, height, radius, radius);
        }
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, width, height, radius, radius);
            g.setStroke(new BasicStroke(1));
        }
        return this;
    }

    /** A stroke of highlighter over a run of text. */
    DocumentPainter highlight(int x, int y, int width, int height) {
        g.setColor(HIGHLIGHTER);
        g.fillRoundRect(x, y, width, height, 6, 6);
        return this;
    }

    /** Another image painted onto the page, scaled to the box. */
    DocumentPainter image(BufferedImage picture, int x, int y, int width, int height) {
        g.drawImage(picture, x, y, width, height, null);
        return this;
    }

    /**
     * Handwriting: an italic serif with every letter nudged a little off the line and the whole line tilted, the way
     * a quick note on a form is written.
     */
    DocumentPainter handwriting(String text, int x, int y, int size, Color ink) {
        var font = new Font(Font.SERIF, Font.ITALIC, size);
        var metrics = g.getFontMetrics(font);
        var transform = g.getTransform();
        g.rotate(Math.toRadians(HANDWRITING_TILT_DEGREES), x, y);
        g.setFont(font);
        g.setColor(ink);
        float cursor = x;
        for (var c : text.toCharArray()) {
            float dy = (float) ((random.nextDouble() - 0.5) * size * 0.14);
            var letter = String.valueOf(c);
            g.drawString(letter, cursor, y + dy);
            g.drawString(letter, cursor + 0.6f, y + dy);
            cursor += metrics.charWidth(c) * 0.97f;
        }
        g.setTransform(transform);
        // the line is turned about its start, which is where its run starts too
        record(text, x, y, size, cursor - x, HANDWRITING_TILT_DEGREES);
        return this;
    }

    /** A handwritten cross in a checkbox: two pen strokes. */
    DocumentPainter cross(int x, int y, int size, Color ink) {
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int jitter = random.nextInt(3);
        g.drawLine(x + 4, y + 3 + jitter, x + size - 3, y + size - 4);
        g.drawLine(x + size - 4 - jitter, y + 3, x + 3, y + size - 3);
        g.setStroke(new BasicStroke(1));
        return this;
    }

    /** A signature: a scrawl of a few loops, as wide as asked, sitting on the baseline. */
    DocumentPainter signature(int x, int y, int width, Color ink) {
        var path = new Path2D.Double();
        path.moveTo(x, y);
        int loops = 6;
        double step = width / (double) loops;
        double cursor = x;
        for (int i = 0; i < loops; i++) {
            double next = cursor + step;
            path.curveTo(
                    cursor + step * 0.3,
                    y - 26.0 - random.nextInt(22),
                    cursor + step * 0.6,
                    y + 8.0 + random.nextInt(12),
                    next,
                    y - random.nextInt(10));
            cursor = next;
        }
        g.setColor(ink);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setStroke(new BasicStroke(1));
        return this;
    }

    /** An ink stamp: bordered capitals, pressed a little askew. */
    DocumentPainter stamp(String text, int x, int y, Color ink, double angleDegrees) {
        var font = sansBold(34);
        int width = widthOf(text, font) + 44;
        int height = 64;
        var transform = g.getTransform();
        var turn = AffineTransform.getRotateInstance(Math.toRadians(angleDegrees), x + width / 2.0, y + height / 2.0);
        g.transform(turn);
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 185));
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(x, y, width, height, 12, 12);
        g.setFont(font);
        g.drawString(text, x + 22, y + 44);
        g.setStroke(new BasicStroke(1));
        g.setTransform(transform);
        // the stamp is turned about its middle, so its text starts where the turn takes the start of the line
        var start = turn.transform(new Point2D.Double(x + 22.0, y + 44.0), null);
        record(text, start.getX(), start.getY(), font.getSize2D(), widthOf(text, font), angleDegrees);
        return this;
    }

    /** The page as painted, with the text painted on it. */
    Pdfs.Page page() {
        return new Pdfs.Page(image, List.copyOf(runs));
    }

    /**
     * The top of the page down to the given height, as a mail client prints an email to a page that ends where the
     * message does; the text painted below it is left out.
     */
    Pdfs.Page page(int height) {
        return new Pdfs.Page(
                image.getSubimage(0, 0, image.getWidth(), Math.min(height, image.getHeight())),
                runs.stream().filter(run -> run.y() < height).toList());
    }

    /** A run of text where it was painted, turned clockwise by the angle about its start. */
    private void record(String text, double x, double y, double size, double width, double angleDegrees) {
        if (!text.isBlank()) {
            runs.add(new Pdfs.Run(text, x, y, size, width, angleDegrees));
        }
    }

    /**
     * The page as a scanner sees it: laid a little askew on the off-white platen, with the scanner's shadow along the
     * left edge. The same size as the page.
     */
    Pdfs.Page scanned() {
        var scan = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var s = scan.createGraphics();
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        s.setColor(new Color(0xef, 0xee, 0xe9));
        s.fillRect(0, 0, scan.getWidth(), scan.getHeight());
        var askew = AffineTransform.getRotateInstance(
                Math.toRadians(SCAN_TILT_DEGREES), scan.getWidth() / 2.0, scan.getHeight() / 2.0);
        s.transform(askew);
        s.drawImage(image, 0, 0, null);
        s.setTransform(new AffineTransform());
        s.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 44), 70, 0, new Color(0, 0, 0, 0)));
        s.fillRect(0, 0, 70, scan.getHeight());
        s.dispose();
        // the text lies as the page does: each run's start turned with it, and the run turned as much again
        var text = runs.stream()
                .map(run -> {
                    var start = askew.transform(new Point2D.Double(run.x(), run.y()), null);
                    return new Pdfs.Run(
                            run.text(),
                            start.getX(),
                            start.getY(),
                            run.size(),
                            run.width(),
                            run.angle() + SCAN_TILT_DEGREES);
                })
                .toList();
        return new Pdfs.Page(scan, text);
    }

    /**
     * The page as a phone photographs it: on a dark desk, turned a little, under a warm light from the top left,
     * with its shadow. Wider and taller than the page by the desk around it.
     */
    BufferedImage photographed() {
        int margin = 70;
        var photo = new BufferedImage(
                image.getWidth() + 2 * margin, image.getHeight() + 2 * margin, BufferedImage.TYPE_INT_RGB);
        var p = photo.createGraphics();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setPaint(new GradientPaint(
                0, 0, new Color(0x4a, 0x3a, 0x33), 0, photo.getHeight(), new Color(0x26, 0x1e, 0x1b)));
        p.fillRect(0, 0, photo.getWidth(), photo.getHeight());
        p.rotate(Math.toRadians(1.8), photo.getWidth() / 2.0, photo.getHeight() / 2.0);
        p.setColor(new Color(0, 0, 0, 120));
        p.fillRect(margin + 16, margin + 22, image.getWidth(), image.getHeight());
        p.drawImage(image, margin, margin, null);
        p.setTransform(new AffineTransform());
        p.setPaint(new GradientPaint(
                0, 0, new Color(255, 228, 180, 36), photo.getWidth(), photo.getHeight(), new Color(30, 15, 0, 95)));
        p.fillRect(0, 0, photo.getWidth(), photo.getHeight());
        p.dispose();
        return photo;
    }
}
