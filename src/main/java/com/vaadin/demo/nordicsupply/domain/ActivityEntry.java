package com.vaadin.demo.nordicsupply.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One row of the activity log: a turn sent to the model, or a decision taken on a result. A turn has the prompt as it
 * went out, what the model could see with it, the model, and once it has ended, what the model proposed or answered,
 * why it stopped and the tokens it took. A decision is a person's approval of a result or a rule's refusal of a
 * prompt, with the rule. Both name the visit that wrote them, the employee signed in, the view and the time.
 */
@Entity
@Table(name = "activity_log")
public class ActivityEntry {

    /** What was decided on a result: kept by a person, or refused by a rule. */
    public enum Decision {
        APPLIED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "visit_id")
    private UUID visit;

    @Column(name = "user_id")
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_name")
    private ActivityView view;

    @Lob
    private String prompt;

    @Column(name = "data_scope")
    private String dataScope;

    @Lob
    private String proposal;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @Column(name = "rejection_rule")
    private String rejectionRule;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "finish_reason")
    private String finishReason;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    protected ActivityEntry() {}

    private ActivityEntry(LocalDateTime occurredAt, UUID visit, int userId, ActivityView view) {
        this.occurredAt = occurredAt;
        this.visit = visit;
        this.userId = userId;
        this.view = view;
    }

    /** A turn as it leaves for the model. */
    public static ActivityEntry turn(
            LocalDateTime at,
            UUID visit,
            int userId,
            ActivityView view,
            String prompt,
            String modelCouldSee,
            String model) {
        var entry = new ActivityEntry(at, visit, userId, view);
        entry.prompt = prompt;
        entry.dataScope = modelCouldSee;
        entry.modelName = model;
        return entry;
    }

    /** A decision on a result: what was kept or refused, and for a refusal the rule. */
    public static ActivityEntry decision(
            LocalDateTime at,
            UUID visit,
            int userId,
            ActivityView view,
            Decision decision,
            String prompt,
            String proposal,
            String rule) {
        var entry = new ActivityEntry(at, visit, userId, view);
        entry.decision = decision;
        entry.prompt = prompt;
        entry.proposal = proposal;
        entry.rejectionRule = rule;
        return entry;
    }

    /**
     * How the turn ended: the model's answer, or why it failed, which stays unless the turn proposed something, and
     * why the model stopped and the tokens the turn took, each {@code null} when the provider did not say.
     */
    public void end(String answer, String finishReason, Integer inputTokens, Integer outputTokens) {
        if (proposal == null) {
            proposal = answer;
        }
        this.finishReason = finishReason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    /** What the model proposed in the turn, in place of its answer. */
    public void propose(String proposal) {
        this.proposal = proposal;
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ActivityEntry other && id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
