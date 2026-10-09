package com.vaadin.demo.nordicsupply.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.RequestListener.RequestEvent;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import com.vaadin.flow.component.ai.provider.ResponseMetadata.TokenUsage;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * An orchestrator's turn is recorded as it goes out, completed when it ends and again with what the model proposed,
 * and a refused prompt is recorded with its rule.
 */
class TurnLogTest {

    private final ActivityLog log = mock(ActivityLog.class);
    private final ActivityLog.Author author = new ActivityLog.Author(UUID.randomUUID(), 8);
    private final TurnLog turns = new TurnLog(log, author, ActivityView.CLAIM_FORM);

    TurnLogTest() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(42L);
    }

    @Test
    void aTurnIsRecordedAsItGoesOutWithTheNamesOfItsAttachments() {
        var message = new AIAttachment("Wrong colour.png", "image/png", new byte[0]);

        turns.asked(new RequestEvent("Fill in the form.", "m1", List.of(message)), "The message");

        verify(log)
                .asked(
                        author,
                        ActivityView.CLAIM_FORM,
                        "Fill in the form.\n\nAttached: Wrong colour.png",
                        "The message");
    }

    @Test
    void theEndOfATurnRecordsTheAnswerWhyTheModelStoppedAndTheTokens() {
        turns.asked(new RequestEvent("Open claims", "m1", List.of()), "The schema");

        turns.ended(new ResponseEvent("A table of open claims.", null, new ResponseMetadata("STOP", usage(900, 40))));

        verify(log).ended(42L, "A table of open claims.", "STOP", 900, 40);
    }

    /** A provider's message can quote the account's key, so the log keeps the kind of failure alone. */
    @Test
    void aFailedTurnIsRecordedByTheKindOfFailureNeverItsMessage() {
        turns.asked(new RequestEvent("Open claims", "m1", List.of()), "The schema");

        turns.ended(new ResponseEvent("", new IllegalStateException("401: Incorrect API key sk-1234"), null));

        verify(log).ended(42L, "Failed: IllegalStateException", null, null, null);
    }

    /** An interceptor that fails ends a turn that was never asked; the turn before it keeps what it had. */
    @Test
    void aTurnEndsOnceAndATurnNeverAskedLeavesTheLastOneAlone() {
        turns.asked(new RequestEvent("Open claims", "m1", List.of()), "The schema");
        turns.ended(new ResponseEvent("Answer", null, null));

        turns.ended(new ResponseEvent("", new UnsupportedOperationException("interceptor"), null));

        verify(log).ended(eq(42L), anyString(), any(), any(), any());
        verify(log, never()).ended(anyLong(), eq("Failed: UnsupportedOperationException"), any(), any(), any());
    }

    /** The controller reports what the model proposed just after the turn has ended. */
    @Test
    void whatTheModelProposedGoesToTheLatestTurnAfterItHasEnded() {
        turns.asked(new RequestEvent("Open claims", "m1", List.of()), "The schema");
        turns.ended(new ResponseEvent("Answer", null, null));

        turns.proposed("SELECT claim_number FROM claims");

        verify(log).proposed(42L, "SELECT claim_number FROM claims");
    }

    @Test
    void nothingIsProposedBeforeATurnHasBeenAsked() {
        turns.proposed("SELECT 1");

        verify(log, never()).proposed(anyLong(), anyString());
    }

    @Test
    void aRefusedPromptIsRecordedWithItsRule() {
        turns.refused("A very long question…", "A question can have at most 500 characters; this one has 600.");

        verify(log)
                .refused(
                        author,
                        ActivityView.CLAIM_FORM,
                        "A very long question…",
                        "A question can have at most 500 characters; this one has 600.");
        verify(log, never()).asked(any(), any(), anyString(), anyString());
    }

    private static TokenUsage usage(int in, int out) {
        return new TokenUsage(in, out, in + out);
    }
}
