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
        // the head office's region is labelled for the user switcher; in a list of countries it reads as what it covers
        setItemLabelGenerator(scope -> Scopes.ALL.equals(scope) ? "All countries" : scope.label());
        setAriaLabel("Countries");
        addClassName("country-filter");
    }
}
