package com.vaadin.demo.nordicsupply.session;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

/**
 * The view introductions a session has already shown: a readme where the view has one, or the view type otherwise.
 * This lets each view identify its first visit consistently in every tab of the session.
 */
@Component
@VaadinSessionScope
public class ShownReadmes implements Serializable {

    private final Set<String> shown = new HashSet<>();

    /** Records the introduction as shown; {@code true} the first time in the session, {@code false} after that. */
    public boolean firstTime(String introduction) {
        return shown.add(introduction);
    }
}
