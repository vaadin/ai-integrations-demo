package com.vaadin.demo.nordicsupply;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.vaadin.flow.i18n.I18NProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** What the application settles for every machine it runs on. */
@SpringBootTest
class ApplicationTest {

    @Autowired
    private I18NProvider locales;

    @Test
    void theJvmLocaleIsPinnedSoThatSearchBehavesTheSameEverywhere() {
        assertThat(Locale.getDefault()).isEqualTo(Locale.ROOT);
    }

    /** Vaadin takes the session's locale and the page's {@code lang} from the one locale the provider offers. */
    @Test
    void theScreenOffersOneLocaleWhateverTheBrowserAsksFor() {
        assertThat(locales.getProvidedLocales()).containsExactly(Locale.of("en", "FI"));
    }
}
