package com.vaadin.demo.nordicsupply.ai.budget;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.RequestInterceptor;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;

/**
 * What a feature puts before its turns, so that its view need not: each question or message is admitted against the
 * {@link AIBudget}, refused with a sentence for the visitor when it is over a cap or the budget, and the turn is
 * closed when its response comes. One guard serves one orchestrator, which runs one turn at a time. Admission
 * happens on the request thread, where the caller can be read; the close comes on the provider's thread.
 */
public final class TurnGuard {

    private static final String TEXT = "text/plain";

    private final AIBudget budget;
    private final String feature;

    /** The turn under way, until its response closes it; {@code null} between turns. */
    private final AtomicReference<AIBudget.Turn> turn = new AtomicReference<>();

    TurnGuard(AIBudget budget, String feature) {
        this.budget = budget;
        this.feature = feature;
    }

    /** The caps a view applies itself, such as the length its fields take. */
    public AIBudgetProperties limits() {
        return budget.limits();
    }

    /**
     * For {@code AIOrchestrator.Builder.withRequestInterceptor}: a question over the length cap, or one the budget
     * refuses, is answered by the chat with the reason and never sent; any other is admitted as a turn.
     */
    public void admit(RequestInterceptor.RequestInterceptEvent event) {
        var length = event.getUserMessage().length();
        var cap = limits().maxQuestionLength();
        if (length > cap) {
            event.reject("A question can have at most " + cap + " characters; this one has " + length + ".");
            return;
        }
        switch (budget.admit(Caller.current(), feature)) {
            case AIBudget.Refusal refused -> event.reject(refused.reason());
            case AIBudget.Turn admitted -> turn.set(admitted);
        }
    }

    /**
     * Admits a turn the view starts itself, from a message rather than the chat input: admitted, the turn starts;
     * refused, the view is told why.
     *
     * @param start starts the turn, whose response the listener from {@link #closing} must see
     * @param refused receives the sentence for the visitor
     */
    public void admit(Runnable start, Consumer<String> refused) {
        switch (budget.admit(Caller.current(), feature)) {
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
     * message the AI may read; a PDF's pages and the model's own limits bound the rest.
     *
     * @param message the message as it was given
     * @return the sentence for the visitor, or empty
     */
    public Optional<String> refusal(AIAttachment message) {
        var fileCap = limits().maxFileSize();
        if (message.data().length > fileCap.toBytes()) {
            return Optional.of("A file can have at most " + fileCap.toMegabytes() + " MB.");
        }
        if (!TEXT.equals(message.mimeType())) {
            return Optional.empty();
        }
        var length = new String(message.data(), StandardCharsets.UTF_8).length();
        var textCap = limits().maxTextLength();
        if (length <= textCap) {
            return Optional.empty();
        }
        return Optional.of(
                "A message can have at most " + textCap + " characters of text; this one has " + length + ".");
    }
}
