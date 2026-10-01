package com.vaadin.demo.nordicsupply.examples;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.AttachmentContentType;

/**
 * One of the documents the Claims view offers to try the assistant on: a file as it would be sent to the model, a
 * line saying what kind of document it is, and the country of the customer it comes from, so that an account sees
 * the examples of its own region.
 *
 * @param attachment the file: its name, its MIME type and its bytes
 * @param kind what the file is, in a few words, shown under its name
 * @param country the customer's country, a value of the customers' {@code country} column
 */
public record ExampleDocument(AIAttachment attachment, String kind, String country) {

    /** The file name, with its extension. */
    public String name() {
        return attachment.name();
    }

    /** Whether the file is a PDF rather than an image. */
    public boolean isPdf() {
        return AttachmentContentType.fromMimeType(attachment.mimeType()) == AttachmentContentType.PDF;
    }
}
