package com.vaadin.demo.nordicsupply.session;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

/**
 * The readmes a session has already opened by itself, so that each opens on a view's first visit only, in every
 * tab of the session.
 */
@Component
@VaadinSessionScope
public class ShownReadmes implements Serializable {

    private final Set<String> shown = new HashSet<>();

    /** Records the readme as shown; {@code true} the first time in the session, {@code false} after that. */
    public boolean firstTime(String readme) {
        return shown.add(readme);
    }
}
