package com.vaadin.demo.nordicsupply.ai;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parts a widget reply is read for: the title and the summary the model is asked to put on their own lines, and
 * whatever it wrote after them. A part the model left out comes back as an empty string.
 * <p>
 * A streamed reply carries every text the model wrote during the turn, so what it wrote before and between its tool
 * calls comes first and the title line can follow it on the same line; that text is not part of the reply's rest. A
 * line is recognised by its label. At the start of a line the label may be in any case, in Markdown bold, or follow a
 * list mark or a heading mark. After other text it has to be in capitals, as the model was asked to write it, since
 * prose says "in summary:" and "job title:" in lower case.
 */
public record WidgetReply(String title, String summary, String rest) {

    /**
     * A label and its value up to the end of the line. The label is group 1 at the start of a line and group 2 after
     * other text.
     */
    private static final Pattern LINE = Pattern.compile(
            "(?m)(?:^[ \\t]*(?:[-*+#>]+[ \\t]+)?\\**(?i:(TITLE|SUMMARY))|\\**(TITLE|SUMMARY))\\**[ \\t]*:\\**[ \\t]*(.*)$");

    /** What a model puts around a whole value: the prompt's angle brackets, straight and curly quotes. */
    private static final Map<Character, Character> QUOTES = Map.of('"', '"', '\'', '\'', '<', '>', '“', '”');

    /** Reads the two lines out of a reply; the rest is what follows them, with the lines taken out. */
    public static WidgetReply parse(String response) {
        var lines = LINE.matcher(response);
        if (!lines.find()) {
            return new WidgetReply("", "", response.strip());
        }
        var body = response.substring(lines.start());
        var title = "";
        var summary = "";
        var matcher = LINE.matcher(body);
        while (matcher.find()) {
            var value = valueOf(matcher.group(3));
            var label = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (label.equalsIgnoreCase("TITLE")) {
                title = title.isEmpty() ? value : title;
            } else {
                summary = summary.isEmpty() ? value : summary;
            }
        }
        return new WidgetReply(title, summary, LINE.matcher(body).replaceAll("").strip());
    }

    /** A value without Markdown bold and without one pair of quotes around all of it. */
    private static String valueOf(String text) {
        var value = text.replace("**", "").strip();
        if (value.length() > 1 && QUOTES.getOrDefault(value.charAt(0), '\0') == value.charAt(value.length() - 1)) {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }
}
