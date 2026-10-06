package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The regions a demo user can be responsible for are the four the database names, and nothing else; the country filter
 * narrows a region to one of its own countries.
 */
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

    @Test
    void aRegionCanBeNarrowedToEachOfItsOwnCountriesAndNoOther() {
        assertThat(Scopes.filtersWithin(Scopes.FI_EE)).containsExactly(Scopes.FI_EE, Scopes.FI, Scopes.EE);
        assertThat(Scopes.filtersWithin(Scopes.DK_DE)).containsExactly(Scopes.DK_DE, Scopes.DK, Scopes.DE);
        assertThat(Scopes.filtersWithin(Scopes.ALL))
                .containsExactly(Scopes.ALL, Scopes.FI, Scopes.EE, Scopes.SE, Scopes.NO, Scopes.DK, Scopes.DE);
    }
}
