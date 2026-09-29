package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The regions a demo user can be responsible for are the four the database names, and nothing else. */
class ScopesTest {

    @Test
    void eachOfTheFourKeysNamesItsRegion() {
        assertThat(Scopes.roleScope("all")).contains(Scopes.ALL);
        assertThat(Scopes.roleScope("fi_ee")).contains(Scopes.FI_EE);
        assertThat(Scopes.roleScope("se_no")).contains(Scopes.SE_NO);
        assertThat(Scopes.roleScope("dk_de")).contains(Scopes.DK_DE);
    }

    @Test
    void theFourRegionsComeHeadOfficeFirst() {
        assertThat(Scopes.roleScopes()).containsExactly(Scopes.ALL, Scopes.FI_EE, Scopes.SE_NO, Scopes.DK_DE);
    }

    @Test
    void anyOtherKeyNamesNothing() {
        assertThat(Scopes.roleScope("fi")).isEmpty();
        assertThat(Scopes.roleScope("")).isEmpty();
    }
}
