package com.vaadin.demo.nordicsupply.util;

import org.springframework.core.NestedExceptionUtils;

/**
 * What to say when something underneath fails. A JDBC failure arrives wrapped in several layers; the innermost
 * message is the one that says anything, and it is clipped so that what is shown or sent on stays readable.
 */
public final class Errors {

    private Errors() {}

    /**
     * The root cause's message, cut to {@code maxLength} characters with an ellipsis when it was cut; a root cause
     * without a message is named by its type. A character outside the Basic Multilingual Plane, such as an emoji,
     * counts as one and is never cut in half: half of one is not valid text, and a model's API may refuse the whole
     * request that carries it.
     */
    public static String rootMessage(Throwable t, int maxLength) {
        var cause = NestedExceptionUtils.getMostSpecificCause(t);
        var message = cause.getMessage() != null
                ? cause.getMessage()
                : cause.getClass().getSimpleName();
        return message.codePointCount(0, message.length()) > maxLength
                ? message.substring(0, message.offsetByCodePoints(0, maxLength)) + "…"
                : message;
    }
}
