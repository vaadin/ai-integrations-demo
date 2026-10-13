package com.vaadin.demo.nordicsupply.ai.budget;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.ai.common.AIAttachment;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** The guard refuses what the AI may not read, with the sentence for the visitor, and lets the rest through. */
class TurnGuardTest {

    private final TurnGuard guard = Budgets.budget().guard("claims");

    /** The cap on a file's size holds on the server too, where a request made outside the browser lands. */
    @Test
    void aFileOverTheCapIsRefused() {
        var cap = 10 * 1024 * 1024;

        assertThat(guard.refusal(new AIAttachment("photo.jpg", "image/jpeg", new byte[cap + 1])))
                .contains("A file can have at most 10 MB.");
        assertThat(guard.refusal(new AIAttachment("photo.jpg", "image/jpeg", new byte[cap])))
                .isEmpty();
    }

    /** A cap that is not a whole number of megabytes is named as it was set, not rounded down to one. */
    @Test
    void aFileCapIsNamedAsItWasSet() {
        var kilobytes = Budgets.withMaxFileSize(DataSize.ofKilobytes(300)).guard("claims");

        assertThat(kilobytes.refusal(new AIAttachment("scan.pdf", "application/pdf", new byte[300 * 1024 + 1])))
                .contains("A file can have at most 300 KB.");
        assertThat(guard.fileCap()).isEqualTo("10 MB");
        assertThat(Budgets.withMaxFileSize(DataSize.ofBytes(1500))
                        .guard("claims")
                        .fileCap())
                .isEqualTo("1500 bytes");
    }

    /** The cap on a text holds for an uploaded file as for a pasted text; a PDF or an image is bounded otherwise. */
    @Test
    void aTextOverTheCapIsRefusedAndAPdfIsNotCappedAsText() {
        var longText = new AIAttachment(
                "Order 17.eml", "text/plain", "x".repeat(50_000).getBytes(UTF_8));
        var shortText = new AIAttachment("note.txt", "text/plain", "Three cartons missing.".getBytes(UTF_8));
        var longPdf = new AIAttachment("scan.pdf", "application/pdf", new byte[60_000]);

        assertThat(guard.refusal(longText))
                .contains("A message can have at most 20000 characters of text; this one has 50000.");
        assertThat(guard.refusal(shortText)).isEmpty();
        assertThat(guard.refusal(longPdf)).isEmpty();
    }
}
