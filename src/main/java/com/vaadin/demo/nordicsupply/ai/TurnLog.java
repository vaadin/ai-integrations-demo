package com.vaadin.demo.nordicsupply.ai;

import java.util.stream.Collectors;

import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.form.FormAIController;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.ai.orchestrator.RequestInterceptor;
import com.vaadin.flow.component.ai.orchestrator.RequestListener;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import com.vaadin.flow.function.SerializableSupplier;

import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * One orchestrator's turns in the activity log, so that its view need not write them: the view passes its hooks
 * through this, and each records its part. The request listener records the prompt as it goes out, the response
 * listener how the turn ended, and the controller's change listener, which the orchestrator calls just after the
 * response listener, what the model proposed. A prompt the request interceptor refuses never reaches the listeners,
 * so the interceptor's wrapper records the refusal.
 * <p>
 * One turn runs at a time, since the orchestrator ignores a prompt sent while one is under way. The request listener
 * runs on the UI thread, the response listener on whichever thread ends the turn.
 */
public class TurnLog {

    /** As much of a refused prompt as the log keeps: what a question may have, since the longest are refused. */
    private static final int KEPT_OF_A_REFUSED_PROMPT = 500;

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
     * For {@code AIOrchestrator.Builder.withRequestListener}: records the prompt as it goes out, with the names of its
     * attachments and what else the view's model may read.
     */
    public void asked(RequestListener.RequestEvent event) {
        var prompt = event.getUserMessage();
        if (!event.getAttachments().isEmpty()) {
            prompt += "\n\nAttached: "
                    + event.getAttachments().stream().map(AIAttachment::name).collect(Collectors.joining(", "));
        }
        turn = log.asked(author, view, prompt, view.modelCouldSee());
        underWay = true;
    }

    /**
     * For {@code AIOrchestrator.Builder.withRequestListener}, around the view's own listener: records the prompt, then
     * passes the event on.
     */
    public RequestListener asking(RequestListener next) {
        return event -> {
            asked(event);
            next.onRequest(event);
        };
    }

    /**
     * For {@code AIOrchestrator.Builder.withResponseListener}, around the view's own listener: records how the turn
     * ended, then passes the event on.
     */
    public ResponseListener ending(ResponseListener next) {
        return event -> {
            ended(event);
            next.onResponse(event);
        };
    }

    /**
     * For {@code AIOrchestrator.Builder.withRequestInterceptor}, around the view's own rules: runs them, and records a
     * prompt they refuse with the reason the chat gives, keeping the first 500 characters of it. A verdict the rules
     * postpone is not followed; the demo's rules decide at once.
     */
    public RequestInterceptor refusing(RequestInterceptor rules) {
        return event -> {
            rules.intercept(event);
            if (event.isRejected()) {
                refused(kept(event.getUserMessage()), event.getRejectionMessage());
            }
        };
    }

    /** Records what the model proposed in each turn of a table: the query the grid shows. */
    public void watch(GridAIController controller) {
        controller.addStateChangeListener(state -> proposed(state.query()));
    }

    /** Records what the model proposed in each turn of a chart: the queries it draws. */
    public void watch(ChartAIController controller) {
        controller.addStateChangeListener(state -> proposed(String.join("\n\n", state.queries())));
    }

    /**
     * Records what the model proposed in each turn of a form: the form as the AI left it, in the form's own words. The
     * form filler reports each field a turn changed once the turn has ended.
     */
    public void watch(FormAIController controller, SerializableSupplier<String> form) {
        controller.addFieldValueChangeListener(event -> proposed(form.get()));
    }

    /** Records a result a person kept from the AI's work, in the view's own words. */
    public void applied(String what) {
        log.applied(author, view, what);
    }

    /** The answer, or the kind of failure, never its message, which can quote the provider's account details. */
    void ended(ResponseListener.ResponseEvent event) {
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

    /** What the model proposed in the latest turn. */
    void proposed(String proposal) {
        var latest = turn;
        if (latest != null) {
            log.proposed(latest, proposal);
        }
    }

    /** A prompt a rule refused, and the rule. */
    void refused(String prompt, String rule) {
        log.refused(author, view, prompt, rule);
    }

    private static String kept(String prompt) {
        if (prompt.codePointCount(0, prompt.length()) <= KEPT_OF_A_REFUSED_PROMPT) {
            return prompt;
        }
        var kept = prompt.codePoints().limit(KEPT_OF_A_REFUSED_PROMPT).toArray();
        return new String(kept, 0, kept.length) + "…";
    }
}
