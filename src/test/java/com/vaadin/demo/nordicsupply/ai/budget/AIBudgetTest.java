package com.vaadin.demo.nordicsupply.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.unit.DataSize;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Admission;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Refusal;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Turn;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Instance;

/**
 * A turn is admitted while its address and the instance are within their caps, refused with a word to the visitor
 * otherwise, named by its cap; the day passes on the budget's clock, and a closed turn frees what it held. Every turn
 * and every refusal goes to the usage log with what was asked.
 */
@ExtendWith(OutputCaptureExtension.class)
class AIBudgetTest {

    /** A clock the test moves forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Address ADDRESS = new Address(120, 3);
    private static final Instance INSTANCE = new Instance(3_000_000, 20_000, 6);
    private static final Duration DEADLINE = Duration.ofMinutes(20);
    private static final Caller LARS = new Caller(UUID.randomUUID(), "203.0.113.7");
    private static final Caller MAIJA = new Caller(UUID.randomUUID(), "198.51.100.2");

    /** What the tests ask, unless one asks something of its own. */
    private static final String QUESTION = "Late shipments last month, by week";

    private final TestClock clock = new TestClock();

    private final UsageLog usage = mock(UsageLog.class);

    private AIBudget budget(Address address, Instance instance) {
        return new AIBudget(
                new AIBudgetProperties(true, 500, 20_000, DataSize.ofMegabytes(10), DEADLINE, address, instance),
                usage,
                clock);
    }

    /** The caller asks the feature the tests' question. */
    private static Request ask(Caller caller, String feature) {
        return new Request(caller, feature, QUESTION, List.of());
    }

    private static Turn admitted(Admission admission) {
        assertThat(admission).isInstanceOf(Turn.class);
        return (Turn) admission;
    }

    private static Refusal refused(Admission admission) {
        assertThat(admission).isInstanceOf(Refusal.class);
        return (Refusal) admission;
    }

    /** A turn that ended with the model reporting the given tokens. */
    private static ResponseEvent answered(int tokens) {
        return new ResponseEvent(
                "Done.", null, new ResponseMetadata("stop", new ResponseMetadata.TokenUsage(tokens - 10, 10, tokens)));
    }

    @Test
    void withinTheLimitsEveryTurnIsAdmitted() {
        var budget = budget(ADDRESS, INSTANCE);

        assertThat(budget.admit(ask(LARS, "insights"))).isInstanceOf(Turn.class);
        assertThat(budget.admit(ask(LARS, "claims"))).isInstanceOf(Turn.class);
        assertThat(budget.admit(ask(MAIJA, "insights"))).isInstanceOf(Turn.class);
        assertThat(budget.paused()).isEmpty();
    }

    @Test
    void anAddressOverItsDayCapWaitsForTomorrow() {
        var budget = budget(new Address(2, 3), INSTANCE);
        admitted(budget.admit(ask(LARS, "insights"))).abandon();
        admitted(budget.admit(ask(LARS, "insights"))).abandon();

        assertThat(refused(budget.admit(ask(LARS, "insights"))))
                .isEqualTo(new Refusal(
                        "address.turns-per-day",
                        "You have reached today's limit of 2 questions; the AI is available to you again tomorrow."));
        // another address is not held back by Lars's
        assertThat(budget.admit(ask(MAIJA, "insights"))).isInstanceOf(Turn.class);

        clock.advance(Duration.ofHours(14)); // past midnight in the clock's zone

        assertThat(budget.admit(ask(LARS, "insights"))).isInstanceOf(Turn.class);
    }

    @Test
    void theSessionsOfOneAddressShareItsCaps() {
        var budget = budget(new Address(2, 3), INSTANCE);
        var otherTab = new Caller(UUID.randomUUID(), LARS.address());
        admitted(budget.admit(ask(LARS, "insights"))).abandon();
        admitted(budget.admit(ask(otherTab, "insights"))).abandon();

        assertThat(refused(budget.admit(ask(new Caller(UUID.randomUUID(), LARS.address()), "insights")))
                        .reason())
                .isEqualTo("You have reached today's limit of 2 questions; the AI is available to you again tomorrow.");
        assertThat(budget.admit(ask(MAIJA, "insights"))).isInstanceOf(Turn.class);
    }

    @Test
    void anIPv6AddressIsCountedByItsPrefix() {
        var budget = budget(new Address(1, 3), INSTANCE);
        admitted(budget.admit(ask(new Caller(UUID.randomUUID(), "2001:db8:1:2:aaaa::1"), "insights")))
                .abandon();

        assertThat(budget.admit(ask(new Caller(UUID.randomUUID(), "2001:db8:1:2:bbbb::2"), "insights")))
                .isInstanceOf(Refusal.class);
        assertThat(budget.admit(ask(new Caller(UUID.randomUUID(), "2001:db8:1:3::1"), "insights")))
                .isInstanceOf(Turn.class);
    }

    @Test
    void anAddressHasAFewTurnsUnderWayAtOnce() {
        var budget = budget(new Address(120, 2), INSTANCE);
        var first = admitted(budget.admit(ask(LARS, "insights")));
        admitted(budget.admit(ask(LARS, "claims")));

        assertThat(refused(budget.admit(ask(LARS, "insights"))))
                .isEqualTo(new Refusal(
                        "address.concurrent-turns", "You have 2 questions in progress; wait for one to finish."));

        first.close(answered(100));

        assertThat(budget.admit(ask(LARS, "insights"))).isInstanceOf(Turn.class);
    }

    @Test
    void theInstanceHasAsManyTurnsUnderWayAsItAffords() {
        var budget = budget(ADDRESS, new Instance(3_000_000, 20_000, 1));
        var running = admitted(budget.admit(ask(LARS, "insights")));

        assertThat(refused(budget.admit(ask(MAIJA, "insights"))))
                .isEqualTo(new Refusal("instance.concurrent-turns", AIBudget.BUSY));
        // being busy is a moment, not the day: the page shows no notice
        assertThat(budget.paused()).isEmpty();

        running.close(answered(100));

        assertThat(budget.admit(ask(MAIJA, "insights"))).isInstanceOf(Turn.class);
    }

    @Test
    void theInstanceStopsForTheDayAtItsTokenBudget() {
        var budget = budget(ADDRESS, new Instance(100, 20_000, 6));
        admitted(budget.admit(ask(LARS, "insights"))).close(answered(60));

        // under the budget still: the next turn starts, and may run over it
        admitted(budget.admit(ask(LARS, "insights"))).close(answered(60));

        assertThat(refused(budget.admit(ask(MAIJA, "insights"))))
                .isEqualTo(new Refusal("instance.tokens-per-day", AIBudget.EXHAUSTED));
        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);

        clock.advance(Duration.ofDays(1));

        assertThat(budget.paused()).isEmpty();
        assertThat(budget.admit(ask(LARS, "claims"))).isInstanceOf(Turn.class);
    }

    /**
     * A turn that ended without reported tokens may still have paid for model calls, a tool round before the call that
     * failed for one: it counts for the allowance. A turn that never ran counts nothing.
     */
    @Test
    void aTurnWithoutReportedTokensCountsTheAllowance() {
        var budget = budget(ADDRESS, new Instance(100, 60, 6));
        admitted(budget.admit(ask(LARS, "insights"))).abandon();
        admitted(budget.admit(ask(LARS, "insights")))
                .close(new ResponseEvent(null, new RuntimeException("down"), null));

        // one allowance so far, under the budget
        assertThat(budget.paused()).isEmpty();

        admitted(budget.admit(ask(LARS, "insights")))
                .close(new ResponseEvent("Done.", null, new ResponseMetadata("stop", null)));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    @Test
    void switchedOffNobodyGetsATurn() {
        var budget = new AIBudget(
                new AIBudgetProperties(false, 500, 20_000, DataSize.ofMegabytes(10), DEADLINE, ADDRESS, INSTANCE),
                usage,
                clock);

        assertThat(refused(budget.admit(ask(LARS, "insights")))).isEqualTo(new Refusal("enabled", AIBudget.DISABLED));
        assertThat(budget.paused()).contains(AIBudget.DISABLED);
    }

    @Test
    void aTurnClosedTwiceFreesItsSlotOnce() {
        var budget = budget(new Address(120, 1), INSTANCE);
        var first = admitted(budget.admit(ask(LARS, "insights")));
        first.close(answered(100));
        first.abandon();
        admitted(budget.admit(ask(LARS, "insights")));

        assertThat(budget.admit(ask(LARS, "insights"))).isInstanceOf(Refusal.class);
    }

    /**
     * A refused request leaves no entry behind, however many fresh sessions ask from one address; an address is kept
     * for an admitted turn alone, and dropped once it is idle since an earlier day and the addresses have grown many.
     */
    @Test
    void refusedRequestsLeaveNoEntriesAndIdleOnesAreDropped() {
        var budget = budget(new Address(1, 3), INSTANCE);
        admitted(budget.admit(ask(LARS, "insights"))).abandon();
        for (int i = 0; i < 5000; i++) {
            assertThat(budget.admit(ask(new Caller(UUID.randomUUID(), LARS.address()), "insights")))
                    .isInstanceOf(Refusal.class);
        }

        assertThat(budget.tracked()).isEqualTo(1);

        // many sessions admitted today, each its own address: kept while the day lasts
        for (int i = 0; i < AIBudget.PRUNE_AT; i++) {
            admitted(budget.admit(
                            ask(new Caller(UUID.randomUUID(), "10.0." + (i / 250) + "." + (i % 250)), "insights")))
                    .abandon();
        }
        assertThat(budget.tracked()).isGreaterThan(AIBudget.PRUNE_AT);

        clock.advance(Duration.ofDays(1));
        admitted(budget.admit(ask(MAIJA, "insights"))).abandon();

        assertThat(budget.tracked()).isEqualTo(1);
    }

    /**
     * A turn whose end never comes is let go after the deadline, and not a moment before: its slots are freed and it
     * is counted for the allowance. Nothing can stop its model work, so should its response arrive after all, the
     * tokens it reports take the allowance's place, once; a response that arrives twice counts once.
     */
    @Test
    void aTurnOpenPastTheDeadlineIsLetGoAndALateResponseIsStillCounted(CapturedOutput output) {
        var budget = budget(new Address(120, 1), new Instance(130_000, 20_000, 6));
        var asked = ask(LARS, "insights");
        var late = admitted(budget.admit(asked));
        clock.advance(DEADLINE.minusSeconds(1));

        assertThat(budget.admit(ask(LARS, "claims"))).isInstanceOf(Refusal.class);

        clock.advance(Duration.ofSeconds(1));
        var next = admitted(budget.admit(ask(LARS, "claims")));

        assertThat(output.getOut()).contains("AI turn let go: feature=insights", "tokens=unreported, counted as 20000");
        verify(usage).turnEnded(asked, "let go", null, DEADLINE);

        var response = answered(100_000);
        late.close(response);
        late.close(answered(100_000));

        // 100,000 counted in place of the 20,000, and once: 200,000 would be over the budget
        assertThat(budget.paused()).isEmpty();
        assertThat(output.getOut()).contains("AI turn answered late: feature=insights", "tokens=99990+10");
        verify(usage).turnEnded(asked, "answered late", response, DEADLINE);
        verify(usage, times(1)).turnEnded(any(), eq("answered late"), any(), any());

        next.close(answered(40_000));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    /** A turn let go whose response fails after all keeps the allowance it was counted for. */
    @Test
    void aTurnLetGoThatFailsLateKeepsTheAllowance() {
        var budget = budget(ADDRESS, new Instance(50_000, 20_000, 6));
        var late = admitted(budget.admit(ask(LARS, "insights")));
        clock.advance(DEADLINE);
        admitted(budget.admit(ask(MAIJA, "insights"))).abandon();
        late.close(new ResponseEvent(null, new RuntimeException("down"), null));

        // the allowance once: 20,000 and 29,000 stay under 50,000, one more turn does not
        admitted(budget.admit(ask(LARS, "insights"))).close(answered(29_000));

        assertThat(budget.paused()).isEmpty();

        admitted(budget.admit(ask(LARS, "insights"))).close(answered(1_000));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    /**
     * Every turn and every refusal is a line in the log: which visit, which feature, what became of it; never the
     * question, nor the address it came from.
     */
    @Test
    void everyTurnAndEveryRefusalIsOneLineInTheLog(CapturedOutput output) {
        var budget = budget(new Address(1, 3), INSTANCE);
        admitted(budget.admit(ask(LARS, "insights"))).close(answered(120));
        budget.admit(ask(LARS, "claims"));

        assertThat(output.getOut())
                .contains("AI turn answered: feature=insights visit=" + LARS.visit() + " tokens=110+10 duration=0ms")
                .contains("AI turn refused: feature=claims visit=" + LARS.visit()
                        + " cap=address.turns-per-day"
                        + " reason=\"You have reached today's limit of 1 questions;")
                .doesNotContain(QUESTION)
                .doesNotContain("203.0.113.7");
    }

    /**
     * Every turn goes to the usage log with what was asked, how the turn ended and how long it took, and every refusal
     * with its cap.
     */
    @Test
    void everyTurnAndEveryRefusalGoesToTheUsageLog() {
        var budget = budget(new Address(3, 3), INSTANCE);
        var chart = new Request(LARS, "insights", "Orders per month this year", List.of());
        var claim = new Request(
                LARS,
                "claims",
                "Fill in the claim form from the attached customer message.",
                List.of(new AIAttachment(
                        "Order 17.eml", "text/plain", "Three cartons missing.".getBytes(StandardCharsets.UTF_8))));
        var answer = answered(120);
        var failure = new ResponseEvent(null, new IllegalStateException("The model is down"), null);
        var answering = admitted(budget.admit(chart));
        var failing = admitted(budget.admit(claim));
        clock.advance(Duration.ofSeconds(4));
        answering.close(answer);
        failing.close(failure);
        var neverRan = new Request(LARS, "insights", "Open claims by type", List.of());
        admitted(budget.admit(neverRan)).abandon();
        var overTheDay = new Request(LARS, "insights", "Backorder lines by category", List.of());
        budget.admit(overTheDay);

        verify(usage).turnEnded(chart, "answered", answer, Duration.ofSeconds(4));
        verify(usage).turnEnded(claim, "failed", failure, Duration.ofSeconds(4));
        verify(usage).turnEnded(neverRan, "abandoned", null, Duration.ZERO);
        verify(usage).refused(overTheDay, "address.turns-per-day");
    }

    @Test
    void aCallerWithoutASessionOrAnAddressIsCountedToo() {
        var budget = budget(new Address(1, 3), INSTANCE);
        var unknown = new Caller(UUID.randomUUID(), null);
        admitted(budget.admit(ask(unknown, "insights"))).abandon();

        assertThat(budget.admit(ask(unknown, "insights"))).isInstanceOf(Refusal.class);
    }
}
