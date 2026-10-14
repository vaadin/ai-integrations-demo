package com.vaadin.demo.nordicsupply.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.vaadin.experimental.FeatureFlags;
import com.vaadin.experimental.TestFeatureFlags;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceEventBus;
import com.vaadin.flow.server.VaadinSession;

/**
 * A UI for the tests that drive an orchestrator: it has a session, locked by the test's thread, whose service runs
 * every {@code ui.access} at once on that thread and has the AI components' feature flag on. Held in a field by the
 * test, since the current UI is kept only weakly and a garbage collection would otherwise clear it mid-test. A model that
 * answers on another thread has to be waited for with the session unlocked, or its {@code ui.access} never gets in.
 */
public final class TestUi {

    private TestUi() {}

    /** A UI with a session, made current. */
    public static UI withSession() {
        var lock = new ReentrantLock();
        var flags = mock(FeatureFlags.class);
        when(flags.isEnabled(anyString())).thenReturn(true);
        var context = mock(VaadinContext.class);
        when(context.getAttribute(eq(TestFeatureFlags.attributeType()), any()))
                .thenAnswer(call -> TestFeatureFlags.attribute(flags));
        var service = mock(VaadinService.class);
        when(service.getContext()).thenReturn(context);
        // a real service always has one, and Flow reports data provider queries on it
        when(service.getEventBus()).thenReturn(new VaadinServiceEventBus(service));
        when(service.accessSession(any(), any())).thenAnswer(call -> {
            call.getArgument(1, Command.class).execute();
            return CompletableFuture.completedFuture(null);
        });
        var session = new VaadinSession(service) {
            @Override
            public Lock getLockInstance() {
                return lock;
            }
        };
        session.lock();
        var ui = new UI();
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
    }

    /**
     * Makes the UI's browser name the given time zone, as the client details Flow receives when a UI is created do;
     * the other details stay unknown.
     */
    public static void inTimeZone(UI ui, String zoneId) {
        ui.getInternals()
                .setExtendedClientDetails(new ExtendedClientDetails(
                        ui, null, null, null, null, null, null, null, null, null, null, zoneId, null, null, null, null,
                        null, null, null));
    }
}
