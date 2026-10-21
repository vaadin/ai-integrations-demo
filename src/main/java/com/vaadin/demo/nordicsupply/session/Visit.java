package com.vaadin.demo.nordicsupply.session;

import java.util.UUID;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.ai.TurnLog;
import com.vaadin.demo.nordicsupply.ai.budget.UsageLog;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * One visit to the demo, the session of one browser, and the id its rows in the activity log and its events in the
 * usage log are filed under: random, and never the session's own. A visit reads only its own rows in the activity log,
 * and they are deleted when its session ends; the usage log records that it started. Not serializable, like
 * {@link CurrentUser}.
 */
@Component
@VaadinSessionScope
public class Visit {

    private final UUID id = UUID.randomUUID();
    private final ActivityLog log;
    private final UsageLog usage;

    public Visit(ActivityLog log, UsageLog usage) {
        this.log = log;
        this.usage = usage;
    }

    public UUID id() {
        return id;
    }

    /** Who writes to the activity log in this visit while the given employee is signed in. */
    public ActivityLog.Author as(int userId) {
        return new ActivityLog.Author(id, userId);
    }

    /** The turns of one orchestrator of the given view in this visit, while the given employee is signed in. */
    public TurnLog turns(int userId, ActivityView view) {
        return new TurnLog(log, as(userId), view);
    }

    /** The session has started with its first page, whichever it is: the visit is recorded. */
    @PostConstruct
    void started() {
        usage.visitStarted(id);
    }

    /** The session has ended: its rows in the activity log go with it. */
    @PreDestroy
    void ended() {
        log.forget(id);
    }
}
