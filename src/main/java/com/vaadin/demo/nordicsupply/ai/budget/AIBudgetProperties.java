package com.vaadin.demo.nordicsupply.ai.budget;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * How much of the model a visitor gets, the {@code app.ai.*} properties. The demo has no login, so a visitor is
 * known by their address: a session costs nothing to replace, so it carries no cap and is only named in the log. The
 * instance's day budget is the one to size from the key's own spending limit: it is what stops a visitor who comes
 * from many addresses. The address's caps are sized for an office behind one address, not for one person, and the
 * day budget for the model's price: four hundred million tokens is some six thousand turns.
 *
 * @param enabled whether the AI features answer at all; off, every turn is refused with a word to the visitor
 * @param maxQuestionLength the longest question a chat takes, in characters
 * @param maxTextLength the longest text the claim form reads as a message, pasted or uploaded, in characters
 * @param maxFileSize the largest file the claim form reads, checked in the browser and again on the server
 * @param turnDeadline how long a turn may stay open before it is let go, as one whose end never comes: its slots are
 *     freed and it is counted for {@link Instance#tokensPerUnreportedTurn()}, until its response arrives after all.
 *     Longer than the longest turn the bounds on a model call allow, which is the tool calls of a turn, plus one, times
 *     the 60 seconds Spring AI gives one call, times the attempts per call; a turn let go too early runs on uncounted
 *     beside the next
 * @param address what one address gets, an IPv4 address or an IPv6 /64 prefix, all its sessions together
 * @param instance what this instance gives out in all
 */
@ConfigurationProperties("app.ai")
public record AIBudgetProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500") int maxQuestionLength,
        @DefaultValue("20000") int maxTextLength,
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("45m") Duration turnDeadline,
        @DefaultValue Address address,
        @DefaultValue Instance instance) {

    /**
     * What one address gets, all its sessions together.
     *
     * @param turnsPerDay the turns an address may start within one day
     * @param concurrentTurns the turns an address may have under way at once
     */
    public record Address(
            @DefaultValue("500") int turnsPerDay,
            @DefaultValue("10") int concurrentTurns) {}

    /**
     * What this instance gives out in all.
     *
     * @param tokensPerDay the tokens, as the model reports them, the instance's turns may use within one day; a turn
     *     that would start over this total is refused, so the day may run over by the last turn's tokens
     * @param tokensPerUnreportedTurn what a turn counts for when the model reported no tokens: one that failed,
     *     possibly after model calls that were paid for, or one whose end never came; an allowance on the safe side
     * @param concurrentTurns the turns the instance has under way at once
     */
    public record Instance(
            @DefaultValue("400000000") long tokensPerDay,
            @DefaultValue("20000") long tokensPerUnreportedTurn,
            @DefaultValue("50") int concurrentTurns) {}
}
