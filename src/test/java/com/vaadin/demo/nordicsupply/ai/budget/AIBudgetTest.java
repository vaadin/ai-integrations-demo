package com.vaadin.demo.nordicsupply.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Admission;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Refusal;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget.Turn;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Instance;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Session;

/**
 * A turn is admitted while its session, its address and the instance are within their caps, refused with a word to
 * the visitor otherwise; the minute and the day pass on the budget's clock, and a closed turn frees what it held.
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

    private static final Session SESSION = new Session(10, 60);
    private static final Address ADDRESS = new Address(15, 120, 3);
    private static final Instance INSTANCE = new Instance(1500, 3_000_000, 20_000, 6);
    private static final Duration DEADLINE = Duration.ofMinutes(20);
    private static final Caller LARS = new Caller("session-lars", "203.0.113.7");
    private static final Caller MAIJA = new Caller("session-maija", "198.51.100.2");

    private final TestClock clock = new TestClock();

    private AIBudget budget(Session session, Address address, Instance instance) {
        return new AIBudget(new AIBudgetProperties(true, 500, 20_000, DEADLINE, session, address, instance), clock);
    }

    private static Turn admitted(Admission admission) {
        assertThat(admission).isInstanceOf(Turn.class);
        return (Turn) admission;
    }

    private static String refused(Admission admission) {
        assertThat(admission).isInstanceOf(Refusal.class);
        return ((Refusal) admission).reason();
    }

    /** A turn that ended with the model reporting the given tokens. */
    private static ResponseEvent answered(int tokens) {
        return new ResponseEvent(
                "Done.", null, new ResponseMetadata("stop", new ResponseMetadata.TokenUsage(tokens - 10, 10, tokens)));
    }

    @Test
    void withinTheLimitsEveryTurnIsAdmitted() {
        var budget = budget(SESSION, ADDRESS, INSTANCE);

        assertThat(budget.admit(LARS, "insights")).isInstanceOf(Turn.class);
        assertThat(budget.admit(LARS, "claims")).isInstanceOf(Turn.class);
        assertThat(budget.admit(MAIJA, "insights")).isInstanceOf(Turn.class);
        assertThat(budget.paused()).isEmpty();
    }

    @Test
    void aSessionOverItsMinuteCapWaitsForTheNextMinute() {
        var budget = budget(new Session(2, 60), ADDRESS, INSTANCE);
        admitted(budget.admit(LARS, "insights")).abandon();
        admitted(budget.admit(LARS, "insights")).abandon();

        assertThat(refused(budget.admit(LARS, "insights")))
                .isEqualTo("You have sent 2 questions within a minute; wait a little before the next one.");
        // another session is not held back by Lars's
        assertThat(budget.admit(MAIJA, "insights")).isInstanceOf(Turn.class);

        clock.advance(Duration.ofMinutes(1));

        assertThat(budget.admit(LARS, "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void aSessionOverItsDayCapWaitsForTomorrow() {
        var budget = budget(new Session(10, 2), ADDRESS, INSTANCE);
        admitted(budget.admit(LARS, "insights")).abandon();
        admitted(budget.admit(LARS, "insights")).abandon();

        assertThat(refused(budget.admit(LARS, "insights")))
                .isEqualTo("You have reached today's limit of 2 questions; the AI is available to you again tomorrow.");

        clock.advance(Duration.ofHours(14)); // past midnight in the clock's zone

        assertThat(budget.admit(LARS, "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void theSessionsOfOneAddressShareItsCaps() {
        var budget = budget(SESSION, new Address(2, 120, 3), INSTANCE);
        var otherTab = new Caller("session-lars-2", LARS.address());
        admitted(budget.admit(LARS, "insights")).abandon();
        admitted(budget.admit(otherTab, "insights")).abandon();

        assertThat(refused(budget.admit(new Caller("session-lars-3", LARS.address()), "insights")))
                .isEqualTo("You have sent 2 questions within a minute; wait a little before the next one.");
        assertThat(budget.admit(MAIJA, "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void anIPv6AddressIsCountedByItsPrefix() {
        var budget = budget(SESSION, new Address(1, 120, 3), INSTANCE);
        admitted(budget.admit(new Caller("a", "2001:db8:1:2:aaaa::1"), "insights"))
                .abandon();

        assertThat(budget.admit(new Caller("b", "2001:db8:1:2:bbbb::2"), "insights"))
                .isInstanceOf(Refusal.class);
        assertThat(budget.admit(new Caller("c", "2001:db8:1:3::1"), "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void anAddressHasAFewTurnsUnderWayAtOnce() {
        var budget = budget(SESSION, new Address(15, 120, 2), INSTANCE);
        var first = admitted(budget.admit(LARS, "insights"));
        admitted(budget.admit(LARS, "claims"));

        assertThat(refused(budget.admit(LARS, "insights")))
                .isEqualTo("You have 2 questions in progress; wait for one to finish.");

        first.close(answered(100));

        assertThat(budget.admit(LARS, "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void theInstanceHasAsManyTurnsUnderWayAsItAffords() {
        var budget = budget(SESSION, ADDRESS, new Instance(1500, 3_000_000, 20_000, 1));
        var running = admitted(budget.admit(LARS, "insights"));

        assertThat(refused(budget.admit(MAIJA, "insights"))).isEqualTo(AIBudget.BUSY);
        // being busy is a moment, not the day: the page shows no notice
        assertThat(budget.paused()).isEmpty();

        running.close(answered(100));

        assertThat(budget.admit(MAIJA, "insights")).isInstanceOf(Turn.class);
    }

    @Test
    void theInstanceStopsForTheDayAtItsTurnBudget() {
        var budget = budget(SESSION, ADDRESS, new Instance(2, 3_000_000, 20_000, 6));
        admitted(budget.admit(LARS, "insights")).abandon();
        admitted(budget.admit(MAIJA, "insights")).abandon();

        assertThat(refused(budget.admit(LARS, "claims"))).isEqualTo(AIBudget.EXHAUSTED);
        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);

        clock.advance(Duration.ofDays(1));

        assertThat(budget.paused()).isEmpty();
        assertThat(budget.admit(LARS, "claims")).isInstanceOf(Turn.class);
    }

    @Test
    void theInstanceStopsForTheDayAtItsTokenBudget() {
        var budget = budget(SESSION, ADDRESS, new Instance(1500, 100, 20_000, 6));
        admitted(budget.admit(LARS, "insights")).close(answered(60));

        // under the budget still: the next turn starts, and may run over it
        admitted(budget.admit(LARS, "insights")).close(answered(60));

        assertThat(refused(budget.admit(MAIJA, "insights"))).isEqualTo(AIBudget.EXHAUSTED);
        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    /**
     * A turn that ended without reported tokens may still have paid for model calls, a tool round before the call that
     * failed for one: it counts for the allowance. A turn that never ran counts nothing.
     */
    @Test
    void aTurnWithoutReportedTokensCountsTheAllowance() {
        var budget = budget(SESSION, ADDRESS, new Instance(1500, 100, 60, 6));
        admitted(budget.admit(LARS, "insights")).abandon();
        admitted(budget.admit(LARS, "insights")).close(new ResponseEvent(null, new RuntimeException("down"), null));

        // one allowance so far, under the budget
        assertThat(budget.paused()).isEmpty();

        admitted(budget.admit(LARS, "insights"))
                .close(new ResponseEvent("Done.", null, new ResponseMetadata("stop", null)));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    @Test
    void switchedOffNobodyGetsATurn() {
        var budget =
                new AIBudget(new AIBudgetProperties(false, 500, 20_000, DEADLINE, SESSION, ADDRESS, INSTANCE), clock);

        assertThat(refused(budget.admit(LARS, "insights"))).isEqualTo(AIBudget.DISABLED);
        assertThat(budget.paused()).contains(AIBudget.DISABLED);
    }

    @Test
    void aTurnClosedTwiceFreesItsSlotOnce() {
        var budget = budget(SESSION, new Address(15, 120, 1), INSTANCE);
        var first = admitted(budget.admit(LARS, "insights"));
        first.close(answered(100));
        first.abandon();
        admitted(budget.admit(LARS, "insights"));

        assertThat(budget.admit(LARS, "insights")).isInstanceOf(Refusal.class);
    }

    /**
     * A refused request leaves no entry behind, however many fresh sessions ask from one address; a key is kept for
     * an admitted turn alone, and dropped once it is idle since an earlier day and the keys have grown many.
     */
    @Test
    void refusedRequestsLeaveNoEntriesAndIdleOnesAreDropped() {
        var budget = budget(SESSION, new Address(1, 120, 3), new Instance(10_000, 3_000_000, 20_000, 6));
        admitted(budget.admit(LARS, "insights")).abandon();
        for (int i = 0; i < 5000; i++) {
            assertThat(budget.admit(new Caller("fresh-" + i, LARS.address()), "insights"))
                    .isInstanceOf(Refusal.class);
        }

        assertThat(budget.tracked()).isEqualTo(2);

        // many sessions admitted today, each its own address: kept while the day lasts
        for (int i = 0; i < AIBudget.PRUNE_AT; i++) {
            admitted(budget.admit(new Caller("s-" + i, "10.0." + (i / 250) + "." + (i % 250)), "insights"))
                    .abandon();
        }
        assertThat(budget.tracked()).isGreaterThan(AIBudget.PRUNE_AT);

        clock.advance(Duration.ofDays(1));
        admitted(budget.admit(MAIJA, "insights")).abandon();

        assertThat(budget.tracked()).isEqualTo(2);
    }

    /**
     * A turn whose end never comes is let go after the deadline, and not a moment before: its slots are freed and it
     * is counted for the allowance. Nothing can stop its model work, so should its response arrive after all, the
     * tokens it reports take the allowance's place, once; a response that arrives twice counts once.
     */
    @Test
    void aTurnOpenPastTheDeadlineIsLetGoAndALateResponseIsStillCounted(CapturedOutput output) {
        var budget = budget(SESSION, new Address(15, 120, 1), new Instance(1500, 130_000, 20_000, 6));
        var late = admitted(budget.admit(LARS, "insights"));
        clock.advance(DEADLINE.minusSeconds(1));

        assertThat(budget.admit(LARS, "claims")).isInstanceOf(Refusal.class);

        clock.advance(Duration.ofSeconds(1));
        var next = admitted(budget.admit(LARS, "claims"));

        assertThat(output.getOut()).contains("AI turn let go: feature=insights", "tokens=unreported, counted as 20000");

        late.close(answered(100_000));
        late.close(answered(100_000));

        // 100,000 counted in place of the 20,000, and once: 200,000 would be over the budget
        assertThat(budget.paused()).isEmpty();
        assertThat(output.getOut()).contains("AI turn answered late: feature=insights", "tokens=99990+10");

        next.close(answered(40_000));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    /** A turn let go whose response fails after all keeps the allowance it was counted for. */
    @Test
    void aTurnLetGoThatFailsLateKeepsTheAllowance() {
        var budget = budget(SESSION, ADDRESS, new Instance(1500, 50_000, 20_000, 6));
        var late = admitted(budget.admit(LARS, "insights"));
        clock.advance(DEADLINE);
        admitted(budget.admit(MAIJA, "insights")).abandon();
        late.close(new ResponseEvent(null, new RuntimeException("down"), null));

        // the allowance once: 20,000 and 29,000 stay under 50,000, one more turn does not
        admitted(budget.admit(LARS, "insights")).close(answered(29_000));

        assertThat(budget.paused()).isEmpty();

        admitted(budget.admit(LARS, "insights")).close(answered(1_000));

        assertThat(budget.paused()).contains(AIBudget.EXHAUSTED);
    }

    /** Every turn and every refusal is a line in the log: who, which feature, what became of it; never the question. */
    @Test
    void everyTurnAndEveryRefusalIsOneLineInTheLog(CapturedOutput output) {
        var budget = budget(new Session(1, 60), ADDRESS, INSTANCE);
        admitted(budget.admit(LARS, "insights")).close(answered(120));
        budget.admit(LARS, "claims");

        assertThat(output.getOut())
                .contains("AI turn answered: feature=insights session="
                        + Integer.toHexString(LARS.session().hashCode())
                        + " address=203.0.113.7 tokens=110+10 duration=0ms")
                .contains("AI turn refused: feature=claims session="
                        + Integer.toHexString(LARS.session().hashCode())
                        + " address=203.0.113.7 reason=\"You have sent 1 questions within a minute;")
                .doesNotContain(LARS.session());
    }

    @Test
    void aCallerWithoutASessionOrAnAddressIsCountedToo() {
        var budget = budget(new Session(1, 60), ADDRESS, INSTANCE);
        var unknown = new Caller(null, null);
        admitted(budget.admit(unknown, "insights")).abandon();

        assertThat(budget.admit(unknown, "insights")).isInstanceOf(Refusal.class);
    }
}
