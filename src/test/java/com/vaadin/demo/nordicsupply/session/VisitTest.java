package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.ai.budget.Caller;
import com.vaadin.demo.nordicsupply.ai.budget.UsageLog;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * A visit files its rows in the activity log under its own id, whoever is signed in, and takes them along at its
 * end. The usage log records it as it starts, and every view it opens.
 */
class VisitTest {

    private final ActivityLog log = mock(ActivityLog.class);
    private final UsageLog usage = mock(UsageLog.class);

    @Test
    void everyEmployeeOfAVisitWritesUnderItsId() {
        var visit = new Visit(log, usage);

        assertThat(visit.as(1)).isEqualTo(new ActivityLog.Author(visit.id(), 1));
        assertThat(visit.as(8).visit()).isEqualTo(visit.id());
        assertThat(new Visit(log, usage).id()).isNotEqualTo(visit.id());
    }

    /** A view's turns are written under the visit and the employee signed in, for that view. */
    @Test
    void aViewsTurnsAreTheVisitsUnderTheEmployeeSignedIn() {
        var visit = new Visit(log, usage);

        visit.turns(8, ActivityView.CLAIM_FORM).applied("Customer: Aalborg Trekking ApS");

        verify(log).applied(visit.as(8), ActivityView.CLAIM_FORM, "Customer: Aalborg Trekking ApS");
    }

    @Test
    void aVisitThatEndsTakesItsRowsAlong() {
        var visit = new Visit(log, usage);

        visit.ended();

        verify(log).forget(visit.id());
    }

    /** The visit's start goes to the usage log with its address, unknown without a request, and so does every view. */
    @Test
    void aVisitAndEveryViewItOpensGoToTheUsageLog() {
        var visit = new Visit(log, usage);

        visit.started();
        visit.opened("/");
        visit.opened("/dashboard");

        verify(usage).visitStarted(new Caller(visit.id(), null));
        verify(usage).viewOpened(visit.id(), "/");
        verify(usage).viewOpened(visit.id(), "/dashboard");
    }
}
