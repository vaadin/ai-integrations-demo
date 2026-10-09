package com.vaadin.demo.nordicsupply.ui.components;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tool a widget's model names the widget with, registered on the orchestrator with {@code withTools}: a title for
 * the widget's header and a one-sentence description for its chat. The model calls it during a turn, on the provider's
 * thread, where the widget must not be touched; so the tool keeps the name, and the widget takes it when the turn
 * ends.
 */
public class WidgetNaming implements Serializable {

    /** A widget's name as the model gives it. */
    public record Name(String title, String description) implements Serializable {}

    private final AtomicReference<Name> given = new AtomicReference<>();

    /**
     * The tool: Spring AI reads its name, its description and the parameters' descriptions into the tool definition
     * the model is sent.
     *
     * @return what the model is told back
     */
    @Tool(
            name = "name_widget",
            description = "Names the widget for the user: its title and a one-sentence description of what it shows."
                    + " Call it once the widget shows the data, and again when the user asks to rename the widget.")
    public String nameWidget(
            @ToolParam(description = "At most six words naming what the widget shows, e.g. Late shipments, August 2026")
                    String title,
            @ToolParam(
                            description =
                                    "One sentence in business terms saying what the widget contains and what was counted")
                    String description) {
        given.set(new Name(title, description));
        return "The widget is now named " + title + ".";
    }

    /**
     * The name the model gave in the turn under way, taken out for the widget to apply.
     *
     * @return the name, or empty when the model gave none in this turn
     */
    public Optional<Name> take() {
        return Optional.ofNullable(given.getAndSet(null));
    }
}
