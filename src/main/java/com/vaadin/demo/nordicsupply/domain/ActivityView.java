package com.vaadin.demo.nordicsupply.domain;

/** The views that write to the activity log, by the names its {@code view_name} column holds. */
public enum ActivityView {
    DASHBOARD("Dashboard"),
    CLAIM_FORM("Claims");

    private final String label;

    ActivityView(String label) {
        this.label = label;
    }

    /** The view's name in the rail. */
    public String label() {
        return label;
    }
}
