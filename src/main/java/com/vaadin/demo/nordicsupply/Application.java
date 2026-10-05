package com.vaadin.demo.nordicsupply;

import java.util.Locale;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.PageTitleGenerator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * AI integrations demo: the Vaadin AI components at work in the back office of Nordic Supply, a fictional
 * outdoor-equipment wholesaler. One application, one theme, one dataset and one model set-up; each business case is
 * a view of its own.
 */
@SpringBootApplication
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@ColorScheme(ColorScheme.Value.DARK)
@Push
public class Application implements AppShellConfigurator {

    static {
        // Case-insensitive search lower-cases the typed text in the JVM and the columns in H2, both under the JVM's
        // default locale. A locale with its own case rules, Turkish for one, would make the two disagree, so the
        // default is pinned; the Vaadin session gets its own locale from DisplayLocale.
        Locale.setDefault(Locale.ROOT);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
    }

    @Bean
    PageTitleGenerator pageTitleGenerator() {
        return context -> context.value() + " | Nordic Supply (Vaadin AI Integrations Demo)";
    }
}
