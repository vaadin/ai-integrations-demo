package com.vaadin.demo.nordicsupply.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.vaadin.demo.nordicsupply.ai.budget.Caller;

/** The limit counts per address key, in a window that starts with the first message. */
@ExtendWith(OutputCaptureExtension.class)
class FeedbackLimitTest {

    private static final String VISITOR_ADDRESS = "203.0.113.7";
    private static final Caller VISITOR = new Caller(UUID.randomUUID(), VISITOR_ADDRESS);
    private static final Caller OTHER_VISITOR = new Caller(UUID.randomUUID(), "198.51.100.2");

    private final TestClock clock = new TestClock();
    private final FeedbackLimit limit = new FeedbackLimit(clock);

    private void admitted(Caller caller, int messages) {
        for (int i = 0; i < messages; i++) {
            assertThat(limit.admit(caller)).as("message %d", i + 1).isTrue();
        }
    }

    @Test
    void anAddressIsRefusedAtItsLimit(CapturedOutput output) {
        admitted(VISITOR, FeedbackLimit.MESSAGES_PER_ADDRESS);

        assertThat(limit.admit(VISITOR)).isFalse();
        var sameAddressOtherVisit = new Caller(UUID.randomUUID(), VISITOR_ADDRESS);
        assertThat(limit.admit(sameAddressOtherVisit)).isFalse();
        assertThat(limit.admit(OTHER_VISITOR)).isTrue();

        assertThat(output.getOut())
                .contains("Feedback refused: visit=" + VISITOR.visit())
                .contains("Feedback refused: visit=" + sameAddressOtherVisit.visit())
                .doesNotContain(VISITOR_ADDRESS);
    }

    @Test
    void theWindowEndsAnHourAfterTheFirstMessage() {
        admitted(VISITOR, 1);
        clock.advance(Duration.ofMinutes(50));
        admitted(VISITOR, FeedbackLimit.MESSAGES_PER_ADDRESS - 1);
        clock.advance(Duration.ofMinutes(9));

        assertThat(limit.admit(VISITOR)).isFalse();

        clock.advance(Duration.ofMinutes(2));

        admitted(VISITOR, FeedbackLimit.MESSAGES_PER_ADDRESS);
        assertThat(limit.admit(VISITOR)).isFalse();
    }

    @Test
    void twoIPv6AddressesInOneSlash64ShareACount() {
        admitted(new Caller(UUID.randomUUID(), "2001:db8:1:2:aaaa::1"), FeedbackLimit.MESSAGES_PER_ADDRESS);

        assertThat(limit.admit(new Caller(UUID.randomUUID(), "2001:db8:1:2:bbbb::2")))
                .isFalse();
        assertThat(limit.admit(new Caller(UUID.randomUUID(), "2001:db8:1:3::1")))
                .isTrue();
    }

    /** A clock the test moves forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:00Z");

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
}
