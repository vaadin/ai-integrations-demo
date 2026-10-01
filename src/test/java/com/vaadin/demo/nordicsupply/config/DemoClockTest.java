package com.vaadin.demo.nordicsupply.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.ui.TestUi;

/** The demo's calendar stands still on the dataset's day while its clock keeps time. */
class DemoClockTest {

    @Test
    void theDateStandsStillAndTheTimeOfDayIsTheRealOne() {
        var realTime = LocalTime.now(DemoClock.CLOCK.getZone());

        var now = LocalDateTime.now(DemoClock.CLOCK);

        assertThat(now.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(Duration.between(realTime, now.toLocalTime()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void inAnotherZoneTheDateStaysAndTheTimeFollowsThatZone() {
        var utc = DemoClock.CLOCK.withZone(ZoneOffset.UTC);
        var realUtcTime = LocalTime.now(ZoneOffset.UTC);

        assertThat(utc.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(LocalDate.now(utc)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(Duration.between(realUtcTime, LocalTime.now(utc)).abs()).isLessThan(Duration.ofSeconds(5));
        assertThat(DemoClock.CLOCK.withZone(DemoClock.CLOCK.getZone())).isSameAs(DemoClock.CLOCK);
        assertThat(utc).isEqualTo(DemoClock.CLOCK.withZone(ZoneOffset.UTC)).isNotEqualTo(DemoClock.CLOCK);
    }

    /** A chat message stamped by the system clock is dated on the dataset's day, at the time it was written. */
    @Test
    void anInstantKeepsItsTimeOfDayAndTakesTheDatasetsDay() {
        var zone = DemoClock.CLOCK.getZone();
        var written = LocalDateTime.of(2026, 9, 30, 14, 3, 25).atZone(zone).toInstant();

        var dated = DemoClock.CLOCK.onTheDatasetsDay(written);

        assertThat(dated.atZone(zone).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 7, 14, 3, 25));
        assertThat(DemoClock.CLOCK.onTheDatasetsDay(dated)).isEqualTo(dated);
    }

    /**
     * A visitor's clock is in the time zone their browser named when the UI was created, so that a message written at
     * 19:00 in Los Angeles is dated 19:00 on the dataset's day there; without a zone, or with one Java does not know,
     * it is the demo's own clock.
     */
    @Test
    void aVisitorsClockIsInTheZoneTheirBrowserNamed() {
        var ui = new UI();
        var losAngeles = ZoneId.of("America/Los_Angeles");
        var written = Instant.parse("2026-09-30T02:00:00Z");

        assertThat(DemoClock.forVisitor(null)).isSameAs(DemoClock.CLOCK);
        assertThat(DemoClock.forVisitor(ui)).isSameAs(DemoClock.CLOCK);

        TestUi.inTimeZone(ui, "America/Los_Angeles");

        assertThat(DemoClock.forVisitor(ui)
                        .onTheDatasetsDay(written)
                        .atZone(losAngeles)
                        .toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 9, 7, 19, 0));

        TestUi.inTimeZone(ui, "Nowhere/Atlantis");

        assertThat(DemoClock.forVisitor(ui)).isSameAs(DemoClock.CLOCK);
    }
}
