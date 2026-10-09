package com.vaadin.demo.nordicsupply.ai;

import java.util.stream.Collectors;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.RequestListener;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;

import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * One orchestrator's turns in the activity log, written from the orchestrator's own hooks: the request listener
 * records the prompt as it goes out, the response listener how the turn ended, and the controller's change listener,
 * which the orchestrator calls just after the response listener, what the model proposed. A prompt the request
 * interceptor refuses never reaches the listeners, so the interceptor records the refusal itself.
 * <p>
 * One turn runs at a time, since the orchestrator ignores a prompt sent while one is under way. The request listener
 * runs on the UI thread, the response listener on whichever thread ends the turn.
 */
public class TurnLog {

    private final ActivityLog log;
    private final ActivityLog.Author author;
    private final ActivityView view;

    /** The row of the latest turn, which the controller's report of what the model proposed completes. */
    private volatile Long turn;

    /** Whether that turn has yet to end: a turn that fails in the interceptor ends without having been asked. */
    private volatile boolean underWay;

    public TurnLog(ActivityLog log, ActivityLog.Author author, ActivityView view) {
        this.log = log;
        this.author = author;
        this.view = view;
    }

    /**
     * From the request listener: the prompt as it goes out, with the names of its attachments.
     *
     * @param modelCouldSee what else the model may read in the turn, in a sentence
     */
    public void asked(RequestListener.RequestEvent event, String modelCouldSee) {
        var prompt = event.getUserMessage();
        if (!event.getAttachments().isEmpty()) {
            prompt += "\n\nAttached: "
                    + event.getAttachments().stream().map(AIAttachment::name).collect(Collectors.joining(", "));
        }
        turn = log.asked(author, view, prompt, modelCouldSee);
        underWay = true;
    }

    /**
     * From the response listener: the answer, or the kind of failure, never its message, which can quote the
     * provider's account details; and the provider's finish reason and token usage.
     */
    public void ended(ResponseListener.ResponseEvent event) {
        if (!underWay) {
            return;
        }
        underWay = false;
        var metadata = event.getMetadata();
        var usage = metadata.map(ResponseMetadata::tokenUsage);
        log.ended(
                turn,
                event.getError()
                        .map(error -> "Failed: " + error.getClass().getSimpleName())
                        .orElse(event.getResponse()),
                metadata.map(ResponseMetadata::finishReason).orElse(null),
                usage.map(ResponseMetadata.TokenUsage::inputTokens).orElse(null),
                usage.map(ResponseMetadata.TokenUsage::outputTokens).orElse(null));
    }

    /** From a controller's change listener: what the model proposed in the latest turn. */
    public void proposed(String proposal) {
        var latest = turn;
        if (latest != null) {
            log.proposed(latest, proposal);
        }
    }

    /** From the request interceptor: a prompt a rule refused, and the rule. */
    public void refused(String prompt, String rule) {
        log.refused(author, view, prompt, rule);
    }
}
