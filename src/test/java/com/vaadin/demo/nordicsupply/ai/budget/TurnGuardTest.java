package com.vaadin.demo.nordicsupply.ai.budget;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.RequestInterceptor.RequestInterceptEvent;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * The guard refuses what the AI may not read, with the sentence for the visitor, and lets the rest through. Its own
 * refusals go to the usage log named by their caps, as the budget's turns and refusals do.
 */
class TurnGuardTest {

    private static final UUID VISIT = UUID.randomUUID();

    /** The caller of every request here: the visit, and no address, since a test has no request. */
    private static final Caller CALLER = new Caller(VISIT, null);

    private final UsageLog usage = mock(UsageLog.class);

    private final TurnGuard guard = Budgets.budget(usage).guard("claims", VISIT);

    /** The cap on a file's size holds on the server too, where a request made outside the browser lands. */
    @Test
    void aFileOverTheCapIsRefused() {
        var cap = 10 * 1024 * 1024;
        var tooLarge = new AIAttachment("photo.jpg", "image/jpeg", new byte[cap + 1]);

        assertThat(guard.refusal(tooLarge)).contains("A file can have at most 10 MB.");
        assertThat(guard.refusal(new AIAttachment("photo.jpg", "image/jpeg", new byte[cap])))
                .isEmpty();
        // no prompt has been made of a message refused before it is read
        verify(usage).refused(new Request(CALLER, "claims", null, List.of(tooLarge)), "max-file-size");
    }

    /** A cap that is not a whole number of megabytes is named as it was set, not rounded down to one. */
    @Test
    void aFileCapIsNamedAsItWasSet() {
        var kilobytes = Budgets.withMaxFileSize(DataSize.ofKilobytes(300)).guard("claims", VISIT);

        assertThat(kilobytes.refusal(new AIAttachment("scan.pdf", "application/pdf", new byte[300 * 1024 + 1])))
                .contains("A file can have at most 300 KB.");
        assertThat(guard.fileCap()).isEqualTo("10 MB");
        assertThat(Budgets.withMaxFileSize(DataSize.ofBytes(1500))
                        .guard("claims", VISIT)
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
        verify(usage).refused(new Request(CALLER, "claims", null, List.of(longText)), "max-text-length");
    }

    /** A question over the length cap is refused by the chat and goes to the usage log; the budget never sees it. */
    @Test
    void aQuestionOverTheLengthCapIsRefused() {
        var question = "x".repeat(Budgets.DEFAULTS.maxQuestionLength() + 1);
        var event = new RequestInterceptEvent(question, List.of());

        guard.admit(event);

        assertThat(event.isRejected()).isTrue();
        assertThat(event.getRejectionMessage())
                .isEqualTo("A question can have at most 500 characters; this one has 501.");
        verify(usage).refused(new Request(CALLER, "claims", question, List.of()), "max-question-length");
    }

    /**
     * A turn the view starts itself goes to the budget with its prompt and its message, and so to the usage log when
     * its response closes it.
     */
    @Test
    void aTurnTheViewStartsGoesToTheUsageLogWithItsPromptAndMessage() {
        var message = new AIAttachment("Order 17.eml", "text/plain", "Three cartons missing.".getBytes(UTF_8));
        var started = new AtomicBoolean();
        var answer = new ResponseEvent("Done.", null, null);

        guard.admit("Fill in the claim form.", List.of(message), () -> started.set(true), refusal -> {});
        guard.closing(event -> {}).onResponse(answer);

        assertThat(started).isTrue();
        verify(usage)
                .turnEnded(
                        eq(new Request(CALLER, "claims", "Fill in the claim form.", List.of(message))),
                        eq("answered"),
                        same(answer),
                        any());
    }

    /** Within the caps nothing goes to the usage log until a turn ends. */
    @Test
    void aMessageWithinTheCapsIsNotRecordedAsRefused() {
        guard.refusal(new AIAttachment("note.txt", "text/plain", "Three cartons missing.".getBytes(UTF_8)));

        verifyNoInteractions(usage);
    }
}
