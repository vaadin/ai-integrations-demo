package com.vaadin.demo.nordicsupply.ui.views;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The customer messages the Claims view offers to try the AI on, each a file under {@code examples/} on the
 * classpath. They are written from the dataset, so the customer, the sender, the order and the shipment each message
 * names are rows the form can find, as long as the signed-in user's region covers the customer.
 */
enum ExampleFile {
    DAMAGED_PALLET("Damaged pallet.pdf", "Exported email with photo attachment", "icons/file.svg", "application/pdf"),
    MISSING_CARTONS("Missing cartons.eml", "Email", "icons/mail.svg", ExampleFile.TEXT),
    WRONG_COLOUR("wrong-color.txt", "Plain text", "icons/file-text.svg", ExampleFile.TEXT),
    LATE_DELIVERY("Late delivery.eml", "Email", "icons/mail.svg", ExampleFile.TEXT),
    QUALITY_DEFECT("Quality defect.eml", "Email in Swedish", "icons/mail.svg", ExampleFile.TEXT),
    PRICING_DISPUTE("Pricing dispute.eml", "Email", "icons/mail.svg", ExampleFile.TEXT);

    /** How an email reaches the model: as its text, since the providers leave {@code message/rfc822} out. */
    private static final String TEXT = "text/plain";

    private final String fileName;
    private final String description;
    private final String icon;
    private final String mimeType;

    ExampleFile(String fileName, String description, String icon, String mimeType) {
        this.fileName = fileName;
        this.description = description;
        this.icon = icon;
        this.mimeType = mimeType;
    }

    String fileName() {
        return fileName;
    }

    /** What kind of message the file is, under its name. */
    String description() {
        return description;
    }

    String icon() {
        return icon;
    }

    String mimeType() {
        return mimeType;
    }

    /** Where the file is on the classpath, for {@link Class#getResource}. */
    String resource() {
        return "/examples/" + fileName;
    }

    /** The file's content. */
    byte[] bytes() {
        try (var in = ExampleFile.class.getResourceAsStream(resource())) {
            if (in == null) {
                throw new IllegalStateException("no example file " + resource());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
