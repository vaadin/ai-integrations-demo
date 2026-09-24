package com.vaadin.demo.nordicsupply;

import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI integrations demo: the Vaadin AI components at work in the back office of Nordic Supply, a fictional
 * outdoor-equipment wholesaler. One application, one theme, one dataset and one model set-up; each business case is
 * a view of its own.
 */
@SpringBootApplication
public class Application {

    static {
        // Case-insensitive search lower-cases the typed text in the JVM and the columns in H2, both under the JVM's
        // default locale. A locale with its own case rules, Turkish for one, would make the two disagree, so the
        // default is pinned. Without an I18NProvider the Vaadin session takes this locale too, so anything the screen
        // formats without naming a locale follows it, whatever the machine or the browser is set to.
        Locale.setDefault(Locale.ROOT);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
