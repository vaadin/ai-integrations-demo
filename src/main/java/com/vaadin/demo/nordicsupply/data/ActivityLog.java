package com.vaadin.demo.nordicsupply.data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.domain.ActivityEntry;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * The activity log, the audit trail of the demo's AI: every turn a view sends to the model, with what the model could
 * see and what it proposed or answered, and every decision taken on a result, a person keeping it or a rule refusing
 * a prompt. The AI's own hooks write it, through {@code ai.TurnLog}; the Activity log view reads it.
 * <p>
 * Everyone signs in as one of the same four employees, so every row also names the visit that wrote it: a visit reads
 * only its own rows, and {@link #forget} deletes them when the visit's session ends. Times are the demo clock's, in
 * Helsinki.
 */
@Service
public class ActivityLog {

    /** Who writes a row: the visit, and the employee it is signed in as. */
    public record Author(UUID visit, int userId) implements Serializable {}

    private final ActivityEntryRepository entries;

    /** The model every turn goes to, as {@code application.properties} names it. */
    private final String model;

    public ActivityLog(ActivityEntryRepository entries, @Value("${spring.ai.openai.chat.model}") String model) {
        this.entries = entries;
        this.model = model;
    }

    /**
     * A prompt as it leaves for the model, and what the model may see with it.
     *
     * @return the turn's row, which {@link #ended} and {@link #proposed} complete
     */
    @Transactional
    public long asked(Author author, ActivityView view, String prompt, String modelCouldSee) {
        var turn = ActivityEntry.turn(now(), author.visit(), author.userId(), view, prompt, modelCouldSee, model);
        return entries.save(turn).getId();
    }

    /**
     * How a turn ended: the model's answer, or why it failed, and why the model stopped and the tokens the turn took,
     * each {@code null} when the provider did not say. Nothing happens to a turn whose visit has ended.
     */
    @Transactional
    public void ended(long turn, String answer, String finishReason, Integer inputTokens, Integer outputTokens) {
        entries.findById(turn).ifPresent(entry -> entry.end(answer, finishReason, inputTokens, outputTokens));
    }

    /** What the model proposed in a turn, a widget's query or a form's values, which the log keeps over its answer. */
    @Transactional
    public void proposed(long turn, String proposal) {
        entries.findById(turn).ifPresent(entry -> entry.propose(proposal));
    }

    /**
     * A result a person kept: a claim created from the form the AI filled in. A dashboard's save is none: it keeps the
     * dashboard as it is, also after a mere move or resize, which decides nothing about what the AI proposed.
     */
    @Transactional
    public void applied(Author author, ActivityView view, String what) {
        entries.save(ActivityEntry.decision(
                now(), author.visit(), author.userId(), view, ActivityEntry.Decision.APPLIED, null, what, null));
    }

    /** A prompt a rule refused before it reached the model, and the rule. */
    @Transactional
    public void refused(Author author, ActivityView view, String prompt, String rule) {
        entries.save(ActivityEntry.decision(
                now(), author.visit(), author.userId(), view, ActivityEntry.Decision.REJECTED, prompt, null, rule));
    }

    /** Deletes the rows of a visit. */
    @Transactional
    public void forget(UUID visit) {
        entries.deleteByVisit(visit);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(DemoClock.CLOCK);
    }
}
