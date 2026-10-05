package com.vaadin.demo.nordicsupply.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import com.vaadin.flow.component.UI;

/**
 * The demo's clock. The dataset was generated as of 7 September 2026 and ends there, so anything counted from the
 * real calendar would decay: "this month" would read zero from October, "last month" would find no rows. This clock
 * keeps the date at the dataset's day and lets the time of day run, so that counts stay the same whenever the demo
 * runs and timestamps written in one sitting order, unless the sitting crosses midnight in the clock's zone, when the
 * time of day starts over. Everything that needs today's date or the current time reads it here, never from the system
 * clock. Not serializable, which only matters to session replication: a widget holds its visitor's clock.
 */
public final class DemoClock extends Clock {

    /** The dataset's day. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    /** The demo's clock in Helsinki, where Nordic Supply keeps its books. */
    public static final DemoClock CLOCK = new DemoClock(ZoneId.of("Europe/Helsinki"));

    private final ZoneId zone;

    private DemoClock(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public DemoClock withZone(ZoneId zone) {
        return zone.equals(this.zone) ? this : new DemoClock(zone);
    }

    /**
     * The demo's clock in the time zone of the visitor's browser, which Flow receives when the UI is created, so that a
     * chat dated on the dataset's day reads that day wherever the visitor is. The demo's own clock when there is no UI,
     * or the browser named no zone, or one that Java does not know.
     */
    public static DemoClock forVisitor(UI ui) {
        var zoneId = ui == null ? null : ui.getPage().getExtendedClientDetails().getTimeZoneId();
        if (zoneId == null) {
            return CLOCK;
        }
        try {
            return CLOCK.withZone(ZoneId.of(zoneId));
        } catch (DateTimeException e) {
            return CLOCK;
        }
    }

    /** The dataset's day at the real time of day in this clock's zone. */
    @Override
    public Instant instant() {
        return TODAY.atTime(LocalTime.now(zone)).atZone(zone).toInstant();
    }

    /**
     * The given instant's time of day on the dataset's day, in this clock's zone: how a time stamped from the system
     * clock, as the Vaadin components stamp a chat message, reads in the demo. An instant on the dataset's day stays
     * as it is.
     */
    public Instant onTheDatasetsDay(Instant instant) {
        return TODAY.atTime(LocalTime.ofInstant(instant, zone)).atZone(zone).toInstant();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DemoClock other && zone.equals(other.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TODAY, zone);
    }
}
