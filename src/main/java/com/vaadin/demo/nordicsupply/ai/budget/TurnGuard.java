package com.vaadin.demo.nordicsupply.ai.budget;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.RequestInterceptor;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import org.springframework.util.unit.DataSize;

/**
 * What a feature puts before its turns in one visit, so that its view need not: each question or message is admitted
 * against the {@link AIBudget}, refused with a sentence for the visitor when it is over a cap or the budget, and the
 * turn is closed when its response comes. A refusal by one of the guard's own caps goes to the {@link UsageLog}, as the
 * budget's refusals and turns do. One guard serves one orchestrator, which runs one turn at a time. Admission happens
 * on the request thread, where the caller can be read; the close comes on the provider's thread.
 */
public final class TurnGuard {

    /** The guard's own caps, named by their properties under {@code app.ai}. */
    static final String MAX_QUESTION_LENGTH = "max-question-length";

    static final String MAX_TEXT_LENGTH = "max-text-length";

    static final String MAX_FILE_SIZE = "max-file-size";

    private static final String TEXT = "text/plain";

    private static final long KILOBYTE = DataSize.ofKilobytes(1).toBytes();
    private static final long MEGABYTE = DataSize.ofMegabytes(1).toBytes();

    private final AIBudget budget;
    private final UsageLog usage;
    private final String feature;
    private final UUID visit;

    /** The turn under way, until its response closes it; {@code null} between turns. */
    private final AtomicReference<AIBudget.Turn> turn = new AtomicReference<>();

    TurnGuard(AIBudget budget, UsageLog usage, String feature, UUID visit) {
        this.budget = budget;
        this.usage = usage;
        this.feature = feature;
        this.visit = visit;
    }

    /** The caps a view applies itself, such as the length its fields take. */
    public AIBudgetProperties limits() {
        return budget.limits();
    }

    /**
     * The file cap as a visitor reads it, never rounded: in megabytes when it is a whole number of them, otherwise in
     * kilobytes or bytes.
     *
     * @return the cap with its unit, such as "10 MB" or "300 KB"
     */
    public String fileCap() {
        var bytes = limits().maxFileSize().toBytes();
        if (bytes % MEGABYTE == 0) {
            return bytes / MEGABYTE + " MB";
        }
        if (bytes % KILOBYTE == 0) {
            return bytes / KILOBYTE + " KB";
        }
        return bytes + " bytes";
    }

    /**
     * For {@code AIOrchestrator.Builder.withRequestInterceptor}: a question over the length cap, or one the budget
     * refuses, is answered by the chat with the reason and never sent; any other is admitted as a turn.
     */
    public void admit(RequestInterceptor.RequestInterceptEvent event) {
        var request = request(event.getUserMessage(), event.getAttachments());
        var length = event.getUserMessage().length();
        var cap = limits().maxQuestionLength();
        if (length > cap) {
            usage.refused(request, MAX_QUESTION_LENGTH);
            event.reject("A question can have at most " + cap + " characters; this one has " + length + ".");
            return;
        }
        switch (budget.admit(request)) {
            case AIBudget.Refusal refused -> event.reject(refused.reason());
            case AIBudget.Turn admitted -> turn.set(admitted);
        }
    }

    /**
     * Admits a turn the view starts itself, from a message rather than the chat input: admitted, the turn starts;
     * refused, the view is told why.
     *
     * @param prompt what the turn asks the model
     * @param attachments what goes with the prompt
     * @param start starts the turn with that prompt and those attachments, whose response the listener from
     *     {@link #closing} must see
     * @param refused receives the sentence for the visitor
     */
    public void admit(String prompt, List<AIAttachment> attachments, Runnable start, Consumer<String> refused) {
        switch (budget.admit(request(prompt, attachments))) {
            case AIBudget.Turn admitted -> {
                turn.set(admitted);
                start.run();
            }
            case AIBudget.Refusal refusal -> refused.accept(refusal.reason());
        }
    }

    /**
     * For {@code AIOrchestrator.Builder.withResponseListener}: closes the turn first, whatever became of the view, so
     * that the budget has its slot back, and then passes the event on.
     *
     * @param next the view's own listener
     * @return the listener to register
     */
    public ResponseListener closing(ResponseListener next) {
        return event -> {
            var ended = turn.getAndSet(null);
            if (ended != null) {
                ended.close(event);
            }
            next.onResponse(event);
        };
    }

    /**
     * Why a message is not read, if so: a file over the file cap, which the browser checks before sending and a
     * request made outside the browser passes by, or a text over the text cap, pasted or uploaded. Empty for a
     * message the AI may read; a PDF's pages and the model's own limits bound the rest. A refusal goes to the usage
     * log, with the message's name and size and no prompt, since none has been made of it.
     *
     * @param message the message as it was given
     * @return the sentence for the visitor, or empty
     */
    public Optional<String> refusal(AIAttachment message) {
        if (message.data().length > limits().maxFileSize().toBytes()) {
            return refused(message, MAX_FILE_SIZE, "A file can have at most " + fileCap() + ".");
        }
        if (!TEXT.equals(message.mimeType())) {
            return Optional.empty();
        }
        var length = new String(message.data(), StandardCharsets.UTF_8).length();
        var textCap = limits().maxTextLength();
        if (length <= textCap) {
            return Optional.empty();
        }
        return refused(
                message,
                MAX_TEXT_LENGTH,
                "A message can have at most " + textCap + " characters of text; this one has " + length + ".");
    }

    private Optional<String> refused(AIAttachment message, String cap, String reason) {
        usage.refused(request(null, List.of(message)), cap);
        return Optional.of(reason);
    }

    /** A request from the caller of the current request in this guard's visit and feature. */
    private Request request(String prompt, List<AIAttachment> attachments) {
        return new Request(Caller.current(visit), feature, prompt, attachments);
    }
}
