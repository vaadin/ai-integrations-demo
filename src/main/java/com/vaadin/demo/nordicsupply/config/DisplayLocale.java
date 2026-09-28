package com.vaadin.demo.nordicsupply.config;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.i18n.I18NProvider;
import org.springframework.stereotype.Component;

/**
 * The one locale the application offers: English words with Finland's number and date conventions, the designs'
 * {@code 3 060}. Vaadin gives every session the locale its provider offers, writes it into the page's {@code lang}
 * and formats with it, whatever the machine or the browser is set to; the JVM default, pinned to {@code Locale.ROOT}
 * for the search, has no language to offer. The application has no translated texts, so a key is its own
 * translation.
 */
@Component
public class DisplayLocale implements I18NProvider {

    public static final Locale LOCALE = Locale.of("en", "FI");

    @Override
    public List<Locale> getProvidedLocales() {
        return List.of(LOCALE);
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        return key;
    }
}
