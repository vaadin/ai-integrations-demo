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

import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The budget every turn is admitted against, before anything is sent to the model: what a session, an address and
 * the instance may start within a minute and a day, how many turns each may have under way, and the tokens the
 * instance may use in a day, all from {@link AIBudgetProperties}. A view asks with {@link #admit} and gets a
 * {@link Turn} to close when the turn ends, or a {@link Refusal} with the sentence to show. A turn still open after
 * the deadline in {@link AIBudgetProperties#turnDeadline()}, longer than any turn takes, is let go as one whose end
 * never comes, so that it cannot hold a slot until the next restart: its slots are freed and it is counted for the
 * allowance of a turn without reported tokens. Nothing here can stop its model work, so should its response arrive
 * after all, the tokens it reports take the allowance's place, once. The counts live in memory and start over with the
 * instance; the key's own spending limit is what holds across restarts.
 * <p>
 * Every turn and every refusal is one line in the log, with the feature, a short hash of the session, the address,
 * and for a turn its outcome, the tokens the model reported and how long it took: what is needed to see who uses the
 * model and how much, and nothing of what they asked.
 * <p>
 * The whole budget is one lock: admitting and closing a turn touch a few counters, at the pace of people typing
 * questions.
 */
@Component
public class AIBudget {

    /** The outcome of asking for a turn: the turn, or the reason there is none. */
    public sealed interface Admission permits Turn, Refusal {}

    /**
     * No turn, and the sentence that tells the visitor why.
     *
     * @param reason what the visitor is told
     */
    public record Refusal(String reason) implements Admission {}

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

        private final Caller caller;
        private final String feature;
        private final Instant started;
        private final Window session;
        private final Window address;
        private State state = State.OPEN;

        private Turn(Caller caller, String feature, Instant started, Window session, Window address) {
            this.caller = caller;
            this.feature = feature;
            this.started = started;
            this.session = session;
            this.address = address;
        }

        /** The turn ended, answered or failed: its slots are freed and its tokens, when the model reported them, counted. */
        public void close(ResponseListener.ResponseEvent event) {
            end(
                    event.getError().isPresent() ? "failed" : "answered",
                    event.getMetadata().map(ResponseMetadata::tokenUsage).orElse(null));
        }

        /** The turn never ran: its slots are freed, and no tokens are counted. */
        public void abandon() {
            end(ABANDONED, null);
        }

        private void end(String outcome, ResponseMetadata.TokenUsage usage) {
            synchronized (AIBudget.this) {
                switch (state) {
                    case OPEN -> {
                        free();
                        state = State.CLOSED;
                        count(outcome, usage);
                    }
                    case LET_GO -> {
                        // the response came after all: the tokens it reports take the allowance's place
                        state = State.CLOSED;
                        if (usage != null && usage.totalTokens() != null) {
                            instance.tokensInDay -= limits.instance().tokensPerUnreportedTurn();
                        }
                        count(outcome + " late", usage);
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
            session.inFlight--;
            address.inFlight--;
            instance.inFlight--;
        }

        /** Counts the turn's tokens for the day, the reported ones or the allowance, and writes the turn's line. */
        private void count(String outcome, ResponseMetadata.TokenUsage usage) {
            var now = clock.instant();
            instance.roll(now, zone);
            String tokens;
            if (usage != null && usage.totalTokens() != null) {
                instance.tokensInDay += usage.totalTokens();
                tokens = usage.inputTokens() + "+" + usage.outputTokens();
            } else if (ABANDONED.equals(outcome)) {
                tokens = "none";
            } else if (outcome.endsWith(" late")) {
                tokens = "unreported, the " + limits.instance().tokensPerUnreportedTurn() + " counted stand";
            } else {
                var allowance = limits.instance().tokensPerUnreportedTurn();
                instance.tokensInDay += allowance;
                tokens = "unreported, counted as " + allowance;
            }
            LOG.info(
                    "AI turn {}: feature={} session={} address={} tokens={} duration={}ms",
                    outcome,
                    feature,
                    caller.logged(),
                    caller.addressKey(),
                    tokens,
                    Duration.between(started, now).toMillis());
        }
    }

    /** The counts of one key, a session's, an address's or the instance's, rolled over as the minute and the day pass. */
    private static final class Window {
        private long minute = Long.MIN_VALUE;
        private int inMinute;
        private LocalDate day;
        private int inDay;
        private long tokensInDay;
        private int inFlight;

        void roll(Instant now, ZoneId zone) {
            var epochMinute = now.getEpochSecond() / 60;
            if (epochMinute != minute) {
                minute = epochMinute;
                inMinute = 0;
            }
            var today = LocalDate.ofInstant(now, zone);
            if (!today.equals(day)) {
                day = today;
                inDay = 0;
                tokensInDay = 0;
            }
        }

        void start() {
            inMinute++;
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

    /** How many keys the maps hold before the ones idle since an earlier day are dropped. */
    static final int PRUNE_AT = 2000;

    /** The counts of a key not seen: nothing; read for the checks, never written to. */
    private static final Window NONE = new Window();

    private final AIBudgetProperties limits;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<String, Window> sessions = new HashMap<>();
    private final Map<String, Window> addresses = new HashMap<>();
    private final Window instance = new Window();

    /** The turns under way, oldest first. */
    private final Set<Turn> running = new LinkedHashSet<>();

    /** The minute of the last pruning. */
    private long lastPruned = Long.MIN_VALUE;

    @Autowired
    public AIBudget(AIBudgetProperties limits) {
        this(limits, Clock.systemDefaultZone());
    }

    /** A budget on the given clock, whose zone decides when the day turns. */
    AIBudget(AIBudgetProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    /** The limits this budget applies, for the caps a view checks itself, such as the length of a question. */
    public AIBudgetProperties limits() {
        return limits;
    }

    /**
     * Asks for a turn for the caller. Admitted, the turn counts at once and has to be closed when it ends; refused,
     * nothing is counted and the refusal says why.
     *
     * @param caller who asks, as read on the request thread
     * @param feature the feature asking, named in the log
     * @return the turn, or the refusal
     */
    public synchronized Admission admit(Caller caller, String feature) {
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, zone);
        letGoOfStaleTurns(now);
        var refusal = pausedFor(now);
        if (refusal == null && instance.inFlight >= limits.instance().concurrentTurns()) {
            refusal = BUSY;
        }
        if (refusal != null) {
            return refused(caller, feature, refusal);
        }
        prune(now, today);
        // looked up, not created: a refused request leaves no entry behind
        var session = rolled(sessions.get(caller.sessionKey()), now);
        var address = rolled(addresses.get(caller.addressKey()), now);
        if (session.inDay >= limits.session().turnsPerDay()) {
            return refused(caller, feature, dayCap(limits.session().turnsPerDay()));
        }
        if (address.inDay >= limits.address().turnsPerDay()) {
            return refused(caller, feature, dayCap(limits.address().turnsPerDay()));
        }
        if (session.inMinute >= limits.session().turnsPerMinute()) {
            return refused(caller, feature, minuteCap(limits.session().turnsPerMinute()));
        }
        if (address.inMinute >= limits.address().turnsPerMinute()) {
            return refused(caller, feature, minuteCap(limits.address().turnsPerMinute()));
        }
        if (address.inFlight >= limits.address().concurrentTurns()) {
            return refused(
                    caller,
                    feature,
                    "You have " + address.inFlight + " questions in progress; wait for one to finish.");
        }
        var turn = new Turn(
                caller,
                feature,
                now,
                started(sessions, caller.sessionKey(), now),
                started(addresses, caller.addressKey(), now));
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

    private static Refusal refused(Caller caller, String feature, String reason) {
        LOG.warn(
                "AI turn refused: feature={} session={} address={} reason=\"{}\"",
                feature,
                caller.logged(),
                caller.addressKey(),
                reason);
        return new Refusal(reason);
    }

    /**
     * Why the AI answers nobody right now, if so: it is switched off, or the instance has used its day's budget.
     * For a notice on the page; every turn is refused with the same sentence.
     */
    public synchronized Optional<String> paused() {
        return Optional.ofNullable(pausedFor(clock.instant()));
    }

    private String pausedFor(Instant now) {
        if (!limits.enabled()) {
            return DISABLED;
        }
        instance.roll(now, zone);
        if (instance.inDay >= limits.instance().turnsPerDay()
                || instance.tokensInDay >= limits.instance().tokensPerDay()) {
            return EXHAUSTED;
        }
        return null;
    }

    /** The key's counts as of now, for the checks; {@link #NONE} for a key not seen, which is never written to. */
    private Window rolled(Window window, Instant now) {
        if (window == null) {
            return NONE;
        }
        window.roll(now, zone);
        return window;
    }

    /** The key's counts with the turn that starts now, created for a key not seen before. */
    private Window started(Map<String, Window> windows, String key, Instant now) {
        var window = windows.computeIfAbsent(key, k -> new Window());
        window.roll(now, zone);
        window.start();
        return window;
    }

    /**
     * Drops the keys idle since an earlier day, once there are many and at most once a minute: a key is created only
     * for an admitted turn, so a day adds at most as many as the instance admits, and the walk is paid for rarely.
     */
    private void prune(Instant now, LocalDate today) {
        var minute = now.getEpochSecond() / 60;
        if (minute == lastPruned || sessions.size() + addresses.size() <= PRUNE_AT) {
            return;
        }
        lastPruned = minute;
        sessions.values().removeIf(window -> window.idleSince(today));
        addresses.values().removeIf(window -> window.idleSince(today));
    }

    /** How many sessions and addresses the budget keeps counts for, for the tests. */
    synchronized int tracked() {
        return sessions.size() + addresses.size();
    }

    private static String minuteCap(int cap) {
        return "You have sent " + cap + " questions within a minute; wait a little before the next one.";
    }

    private static String dayCap(int cap) {
        return "You have reached today's limit of " + cap + " questions; the AI is available to you again tomorrow.";
    }
}
