package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.select.Select;

import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Scopes;

/**
 * The Dashboard country filter, in the page's heading: the signed-in employee's region first, then each of its
 * countries alone, and nothing wider. The view binds its value to the session's filter.
 */
public class CountryFilter extends Select<Scope> {

    /** @param region the signed-in employee's region, the widest scope the filter offers */
    public CountryFilter(Scope region) {
        setItems(Scopes.filtersWithin(region));
        // a region is labelled for the user switcher; first in a list of its countries, it reads as all of them
        setItemLabelGenerator(scope -> scope.equals(region) ? "All countries" : scope.label());
        setAriaLabel("Countries");
        addClassName("country-filter");
    }
}
