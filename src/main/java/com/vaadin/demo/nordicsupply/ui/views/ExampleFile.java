package com.vaadin.demo.nordicsupply.ui.views;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The customer messages the Claims view offers to try the AI on: the six the dataset ships under
 * {@code documents/emails}, each written from its records to test something a real message does, such as an order
 * number only in a quoted confirmation, no order number at all, a relative date, a language other than English or
 * personal data in the text ({@code documents/INDEX.md} says what). They are offered as documents with pages, which
 * is what the form filler can point into when it reports where a value came from: the emails printed as a mail
 * client prints a message, and the portal message as a screenshot of the portal showing it. The prints, under
 * {@code examples/} on the classpath, are the application's own; the messages in them are the dataset's, as written.
 * The damaged pallet email's photo is left out: it has nothing to read, only to look at. The screen names the files as
 * the design does.
 */
enum ExampleFile {
    DAMAGED_PALLET("Damaged pallet.pdf", "Exported email"),
    MISSING_CARTONS("Missing cartons.pdf", "Exported email"),
    WRONG_COLOUR("wrong-color.png", "Portal screenshot"),
    LATE_DELIVERY("Late delivery.pdf", "Exported email"),
    QUALITY_DEFECT("Quality defect.pdf", "Exported email in Swedish"),
    PRICING_DISPUTE("Pricing dispute.pdf", "Exported email");

    private final String fileName;
    private final String description;

    ExampleFile(String fileName, String description) {
        this.fileName = fileName;
        this.description = description;
    }

    /** The name the screen shows and a download saves the file under. */
    String fileName() {
        return fileName;
    }

    /** What kind of message the file is, under its name. */
    String description() {
        return description;
    }

    /** A page for a print, a picture for a screenshot. */
    String icon() {
        return fileName.endsWith(".pdf") ? "icons/file.svg" : "icons/file-image.svg";
    }

    String mimeType() {
        return ClaimsView.mimeTypeOf(fileName);
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
