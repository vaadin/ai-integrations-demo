package com.vaadin.demo.nordicsupply.session;

import java.util.UUID;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.ai.TurnLog;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * One visit to the demo, the session of one browser, and the id its rows in the activity log are filed under. A visit
 * reads only its own rows, and they are deleted when its session ends. Not serializable, like {@link CurrentUser}.
 */
@Component
@VaadinSessionScope
public class Visit {

    private final UUID id = UUID.randomUUID();
    private final ActivityLog log;

    public Visit(ActivityLog log) {
        this.log = log;
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

    /** The session has ended: its rows in the activity log go with it. */
    @PreDestroy
    void ended() {
        log.forget(id);
    }
}
