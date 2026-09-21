package com.vaadin.demo.nordicsupply;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI integrations demo: the Vaadin AI components at work in the back office of Nordic Supply, a fictional
 * outdoor-equipment wholesaler. One application, one theme, one dataset and one model set-up; each business case is
 * a view of its own.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
