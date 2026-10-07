package com.vaadin.demo.nordicsupply.ai.budget;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much of the model a visitor gets, the {@code app.ai.*} properties. The demo has no login, so a visitor is
 * known by their address: a session costs nothing to replace, so it carries no cap and is only named in the log. The
 * instance's day budget is the one to size from the key's own spending limit: it is what stops a visitor who comes
 * from many addresses.
 *
 * @param enabled whether the AI features answer at all; off, every turn is refused with a word to the visitor
 * @param maxQuestionLength the longest question a chat takes, in characters
 * @param maxTextLength the longest text the claim form reads as a message, pasted or uploaded, in characters
 * @param turnDeadline how long a turn may stay open before it is let go, as one whose end never comes: its slots are
 *     freed and it is counted for {@link Instance#tokensPerUnreportedTurn()}, until its response arrives after all.
 *     Longer than the longest turn the bounds on a model call allow, which is the tool calls of a turn, plus one, times
 *     the call timeout, times the attempts per call; a turn let go too early runs on uncounted beside the next
 * @param address what one address gets, an IPv4 address or an IPv6 /64 prefix, all its sessions together
 * @param instance what this instance gives out in all
 */
@ConfigurationProperties("app.ai")
public record AIBudgetProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500") int maxQuestionLength,
        @DefaultValue("20000") int maxTextLength,
        @DefaultValue("20m") Duration turnDeadline,
        @DefaultValue Address address,
        @DefaultValue Instance instance) {

    /**
     * What one address gets, all its sessions together.
     *
     * @param turnsPerDay the turns an address may start within one day
     * @param concurrentTurns the turns an address may have under way at once
     */
    public record Address(
            @DefaultValue("120") int turnsPerDay,
            @DefaultValue("3") int concurrentTurns) {}

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
            @DefaultValue("3000000") long tokensPerDay,
            @DefaultValue("20000") long tokensPerUnreportedTurn,
            @DefaultValue("6") int concurrentTurns) {}
}
