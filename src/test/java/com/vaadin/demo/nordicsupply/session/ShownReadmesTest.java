package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.spring.scopes.VaadinSessionScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;

/** A session remembers the readmes it has opened, and only its own. */
@SpringBootTest
class ShownReadmesTest {

    @Autowired
    private ConfigurableListableBeanFactory beans;

    /** Without the session scope the bean is one for everyone, and the first visitor's readmes close them for all. */
    @Test
    void eachVaadinSessionHasItsOwn() {
        assertThat(beans.getBeanDefinition("shownReadmes").getScope())
                .isEqualTo(VaadinSessionScope.VAADIN_SESSION_SCOPE_NAME);
    }
}
