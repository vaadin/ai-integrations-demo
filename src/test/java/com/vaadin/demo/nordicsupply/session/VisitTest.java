package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.data.ActivityLog;

/**
 * A visit files its rows in the activity log under its own id, whoever is signed in, and takes them along at its
 * end.
 */
class VisitTest {

    private final ActivityLog log = mock(ActivityLog.class);

    @Test
    void everyEmployeeOfAVisitWritesUnderItsId() {
        var visit = new Visit(log);

        assertThat(visit.as(1)).isEqualTo(new ActivityLog.Author(visit.id(), 1));
        assertThat(visit.as(8).visit()).isEqualTo(visit.id());
        assertThat(new Visit(log).id()).isNotEqualTo(visit.id());
    }

    @Test
    void aVisitThatEndsTakesItsRowsAlong() {
        var visit = new Visit(log);

        visit.ended();

        verify(log).forget(visit.id());
    }
}
