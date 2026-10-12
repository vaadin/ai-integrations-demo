package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.session.Scopes;

/** The country filter offers a region, then each of its countries, each under the name a visitor reads. */
class CountryFilterTest {

    @Test
    void aRegionsFilterOffersAllItsCountriesThenEachOfThem() {
        assertThat(labels(new CountryFilter(Scopes.FI_EE))).containsExactly("All countries", "Finland", "Estonia");
    }

    /** The head office's region is every country, and the filter says so rather than whose region it is. */
    @Test
    void theHeadOfficesFilterOffersAllCountriesThenEachOfThem() {
        assertThat(labels(new CountryFilter(Scopes.ALL)))
                .containsExactly("All countries", "Finland", "Estonia", "Sweden", "Norway", "Denmark", "Germany");
    }

    /** What the filter's list shows, in its order. */
    private static List<String> labels(CountryFilter filter) {
        return filter.getListDataView()
                .getItems()
                .map(filter.getItemLabelGenerator())
                .toList();
    }
}
