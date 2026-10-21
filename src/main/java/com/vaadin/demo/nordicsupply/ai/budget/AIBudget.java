package com.vaadin.demo.nordicsupply.ai.budget;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The budget every turn is admitted against, before anything is sent to the model: what an address may start within
 * a day, how many turns an address and the instance may have under way, and the tokens the instance may use in a
 * day, all from {@link AIBudgetProperties}. A view asks with {@link #admit} and gets a {@link Turn} to close when the
 * turn ends, or a {@link Refusal} with the sentence to show. A turn still open after the deadline in
 * {@link AIBudgetProperties#turnDeadline()}, longer than any turn takes, is let go as one whose end never comes, so
 * that it cannot hold a slot until the next restart: its slots are freed and it is counted for the allowance of a
 * turn without reported tokens. Nothing here can stop its model work, so should its response arrive after all, the
 * tokens it reports take the allowance's place, once. The counts live in memory and start over with the instance;
 * the key's own spending limit is what holds across restarts.
 * <p>
 * Every turn and every refusal is one line in the log, with the feature, the visit, and for a turn its outcome, the
 * tokens the model reported and how long it took, for a refusal the cap and the reason: what is needed to see how much
 * the visits use the model, and nothing of what they asked or of the address they asked from. What they asked goes to
 * the {@link UsageLog}, with the same turns and refusals.
 * <p>
 * The whole budget is one lock: admitting and closing a turn touch a few counters, at the pace of people typing
 * questions.
 */
@Component
public class AIBudget {

    /** The outcome of asking for a turn: the turn, or the reason there is none. */
    public sealed interface Admission permits Turn, Refusal {}

    /**
     * No turn, the cap that refused it, and the sentence that tells the visitor why.
     *
     * @param cap the cap, named by its property under {@code app.ai}
     * @param reason what the visitor is told
     */
    public record Refusal(String cap, String reason) implements Admission {}

    /**
     * A turn under way: it holds one of its caller's and one of the instance's slots until it is closed, with the
     * model's token usage when the turn ended with a response, or abandoned when it never started. A turn that ended
     * without reported tokens, failed after model calls that were paid for or never heard from again, counts for the
     * allowance in {@link AIBudgetProperties.Instance#tokensPerUnreportedTurn()}. A turn let go at the deadline has
     * been counted for that allowance already; closed after all, with tokens, those take the allowance's place.
     * Closing twice does nothing more.
     */
    public final class Turn implements Admission {

        /** Open and holding its slots; let go at the deadline, counted for the allowance; or closed for good. */
        private enum State {
            OPEN,
            LET_GO,
            CLOSED
        }

        private final Request request;
        private final Instant started;
        private final Window address;
        private State state = State.OPEN;

        private Turn(Request request, Instant started, Window address) {
            this.request = request;
            this.started = started;
            this.address = address;
        }

        /** The turn ended, answered or failed: its slots are freed and its tokens, when the model reported them, counted. */
        public void close(ResponseListener.ResponseEvent event) {
            end(event.getError().isPresent() ? "failed" : "answered", event);
        }

        /** The turn never ran: its slots are freed, and no tokens are counted. */
        public void abandon() {
            end(ABANDONED, null);
        }

        private void end(String outcome, ResponseListener.ResponseEvent ended) {
            synchronized (AIBudget.this) {
                switch (state) {
                    case OPEN -> {
                        free();
                        state = State.CLOSED;
                        count(outcome, ended);
                    }
                    case LET_GO -> {
                        // the response came after all: the tokens it reports take the allowance's place
                        state = State.CLOSED;
                        var reported = tokenUsage(ended);
                        if (reported != null && reported.totalTokens() != null) {
                            instance.tokensInDay -= limits.instance().tokensPerUnreportedTurn();
                        }
                        count(outcome + " late", ended);
                    }
                    case CLOSED -> {
                        // closed already; nothing more to count
                    }
                }
            }
        }

        /** Let go at the deadline: the slots are freed and the allowance counted, and the response is still awaited. */
        private void letGo() {
            free();
            state = State.LET_GO;
            count("let go", null);
        }

        private void free() {
            running.remove(this);
            address.inFlight--;
            instance.inFlight--;
        }

        /**
         * Counts the turn's tokens for the day, the reported ones or the allowance, and writes the turn's line and its
         * event in the usage log.
         */
        private void count(String outcome, ResponseListener.ResponseEvent ended) {
            var now = clock.instant();
            instance.roll(now, zone);
            var reported = tokenUsage(ended);
            String tokens;
            if (reported != null && reported.totalTokens() != null) {
                instance.tokensInDay += reported.totalTokens();
                tokens = reported.inputTokens() + "+" + reported.outputTokens();
            } else if (ABANDONED.equals(outcome)) {
                tokens = "none";
            } else if (outcome.endsWith(" late")) {
                tokens = "unreported, the " + limits.instance().tokensPerUnreportedTurn() + " counted stand";
            } else {
                var allowance = limits.instance().tokensPerUnreportedTurn();
                instance.tokensInDay += allowance;
                tokens = "unreported, counted as " + allowance;
            }
            var took = Duration.between(started, now);
            LOG.info(
                    "AI turn {}: feature={} visit={} tokens={} duration={}ms",
                    outcome,
                    request.feature(),
                    request.caller().visit(),
                    tokens,
                    took.toMillis());
            usage.turnEnded(request, outcome, ended, took);
        }
    }

    /** The counts of one key, an address's or the instance's, rolled over as the day passes. */
    private static final class Window {
        private LocalDate day;
        private int inDay;
        private long tokensInDay;
        private int inFlight;

        void roll(Instant now, ZoneId zone) {
            var today = LocalDate.ofInstant(now, zone);
            if (!today.equals(day)) {
                day = today;
                inDay = 0;
                tokensInDay = 0;
            }
        }

        void start() {
            inDay++;
            inFlight++;
        }

        boolean idleSince(LocalDate today) {
            return inFlight == 0 && !today.equals(day);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AIBudget.class);

    static final String DISABLED =
            "The AI features are switched off right now; the rest of the application works as usual.";

    static final String EXHAUSTED = "The AI has used its budget for today; it is available again tomorrow.";

    static final String BUSY = "The AI is busy with other questions right now; try again in a moment.";

    private static final String ABANDONED = "abandoned";

    /** The caps the budget refuses a turn by, named by their properties under {@code app.ai}. */
    static final String ENABLED = "enabled";

    static final String INSTANCE_TOKENS_PER_DAY = "instance.tokens-per-day";

    static final String INSTANCE_CONCURRENT_TURNS = "instance.concurrent-turns";

    static final String ADDRESS_TURNS_PER_DAY = "address.turns-per-day";

    static final String ADDRESS_CONCURRENT_TURNS = "address.concurrent-turns";

    /** How many addresses the map holds before the ones idle since an earlier day are dropped. */
    static final int PRUNE_AT = 2000;

    /** The counts of a key not seen: nothing; read for the checks, never written to. */
    private static final Window NONE = new Window();

    private final AIBudgetProperties limits;
    private final UsageLog usage;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<String, Window> addresses = new HashMap<>();
    private final Window instance = new Window();

    /** The turns under way, oldest first. */
    private final Set<Turn> running = new LinkedHashSet<>();

    /** The minute of the last pruning. */
    private long lastPruned = Long.MIN_VALUE;

    @Autowired
    public AIBudget(AIBudgetProperties limits, UsageLog usage) {
        this(limits, usage, Clock.systemDefaultZone());
    }

    /** A budget on the given clock, whose zone decides when the day turns. */
    AIBudget(AIBudgetProperties limits, UsageLog usage, Clock clock) {
        this.limits = limits;
        this.usage = usage;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    /** The limits this budget applies, for the caps a view checks itself, such as the length of a question. */
    public AIBudgetProperties limits() {
        return limits;
    }

    /**
     * The guard a feature puts before its turns in one visit: it admits each against this budget and closes it when
     * it ends.
     *
     * @param feature the feature, as the logs name it
     * @param visit the visit whose turns it guards
     * @return a guard for one orchestrator
     */
    public TurnGuard guard(String feature, UUID visit) {
        return new TurnGuard(this, usage, feature, visit);
    }

    /**
     * Asks for a turn for the request. Admitted, the turn counts at once and has to be closed when it ends; refused,
     * nothing is counted and the refusal says why.
     *
     * @param request who asks, as read on the request thread, from which feature and what
     * @return the turn, or the refusal
     */
    public synchronized Admission admit(Request request) {
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, zone);
        letGoOfStaleTurns(now);
        var refusal = pausedFor(now);
        if (refusal == null && instance.inFlight >= limits.instance().concurrentTurns()) {
            refusal = new Refusal(INSTANCE_CONCURRENT_TURNS, BUSY);
        }
        if (refusal != null) {
            return refused(request, refusal);
        }
        prune(now, today);
        var key = request.caller().addressKey();
        // looked up, not created: a refused request leaves no entry behind
        var address = rolled(addresses.get(key), now);
        if (address.inDay >= limits.address().turnsPerDay()) {
            return refused(
                    request,
                    new Refusal(ADDRESS_TURNS_PER_DAY, dayCap(limits.address().turnsPerDay())));
        }
        if (address.inFlight >= limits.address().concurrentTurns()) {
            return refused(
                    request,
                    new Refusal(
                            ADDRESS_CONCURRENT_TURNS,
                            "You have " + address.inFlight + " questions in progress; wait for one to finish."));
        }
        var turn = new Turn(request, now, started(key, now));
        instance.start();
        running.add(turn);
        return turn;
    }

    /** Lets go of the turns open since before the deadline: the oldest come first, so the walk stops early. */
    private void letGoOfStaleTurns(Instant now) {
        var stale = new ArrayList<Turn>();
        for (var turn : running) {
            if (turn.started.plus(limits.turnDeadline()).isAfter(now)) {
                break;
            }
            stale.add(turn);
        }
        stale.forEach(Turn::letGo);
    }

    private Refusal refused(Request request, Refusal refusal) {
        LOG.warn(
                "AI turn refused: feature={} visit={} cap={} reason=\"{}\"",
                request.feature(),
                request.caller().visit(),
                refusal.cap(),
                refusal.reason());
        usage.refused(request, refusal.cap());
        return refusal;
    }

    /**
     * Why the AI answers nobody right now, if so: it is switched off, or the instance has used its day's budget.
     * For a notice on the page; every turn is refused with the same sentence.
     */
    public synchronized Optional<String> paused() {
        return Optional.ofNullable(pausedFor(clock.instant())).map(Refusal::reason);
    }

    private Refusal pausedFor(Instant now) {
        if (!limits.enabled()) {
            return new Refusal(ENABLED, DISABLED);
        }
        instance.roll(now, zone);
        if (instance.tokensInDay >= limits.instance().tokensPerDay()) {
            return new Refusal(INSTANCE_TOKENS_PER_DAY, EXHAUSTED);
        }
        return null;
    }

    /** The tokens a response reports, or {@code null} for a turn without one or a response without them. */
    private static ResponseMetadata.TokenUsage tokenUsage(ResponseListener.ResponseEvent ended) {
        return ended == null
                ? null
                : ended.getMetadata().map(ResponseMetadata::tokenUsage).orElse(null);
    }

    /** The key's counts as of now, for the checks; {@link #NONE} for a key not seen, which is never written to. */
    private Window rolled(Window window, Instant now) {
        if (window == null) {
            return NONE;
        }
        window.roll(now, zone);
        return window;
    }

    /** The address's counts with the turn that starts now, created for an address not seen before. */
    private Window started(String address, Instant now) {
        var window = addresses.computeIfAbsent(address, k -> new Window());
        window.roll(now, zone);
        window.start();
        return window;
    }

    /**
     * Drops the addresses idle since an earlier day, once there are many and at most once a minute: an address is
     * created only for an admitted turn, so a day adds at most one per turn, and the walk is paid for rarely.
     */
    private void prune(Instant now, LocalDate today) {
        var minute = now.getEpochSecond() / 60;
        if (minute == lastPruned || addresses.size() <= PRUNE_AT) {
            return;
        }
        lastPruned = minute;
        addresses.values().removeIf(window -> window.idleSince(today));
    }

    /** How many addresses the budget keeps counts for, for the tests. */
    synchronized int tracked() {
        return addresses.size();
    }

    private static String dayCap(int cap) {
        return "You have reached today's limit of " + cap + " questions; the AI is available to you again tomorrow.";
    }
}
