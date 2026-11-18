package com.vaadin.demo.nordicsupply.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.ai.budget.Caller;

/**
 * Limits feedback per client address, in an hour that starts with the address's first message. An IPv6 address counts
 * by its /64 prefix.
 */
@Component
public class FeedbackLimit {

    static final int MESSAGES_PER_ADDRESS = 30;
    static final Duration WINDOW_LENGTH = Duration.ofHours(1);

    private static final Logger LOG = LoggerFactory.getLogger(FeedbackLimit.class);

    private record Window(Instant start, int count) {
        boolean endedBy(Instant now) {
            return !now.isBefore(start.plus(WINDOW_LENGTH));
        }
    }

    private final Clock clock;
    private final Map<String, Window> windowsByAddressKey = new HashMap<>();

    @Autowired
    public FeedbackLimit() {
        this(Clock.systemDefaultZone());
    }

    FeedbackLimit(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one message for the caller's address, or returns false when the address is at its limit. Call it before
     * the send, so that a failed send also counts.
     */
    public synchronized boolean admit(Caller caller) {
        var now = clock.instant();
        windowsByAddressKey.values().removeIf(window -> window.endedBy(now));
        var key = caller.addressKey();
        var window = windowsByAddressKey.get(key);
        if (window != null && window.count() >= MESSAGES_PER_ADDRESS) {
            // the visit only, never the address, which is personal data
            LOG.warn(
                    "Feedback refused: visit={} limit={} messages an hour per address",
                    caller.visit(),
                    MESSAGES_PER_ADDRESS);
            return false;
        }
        windowsByAddressKey.put(
                key, window == null ? new Window(now, 1) : new Window(window.start(), window.count() + 1));
        return true;
    }
}
