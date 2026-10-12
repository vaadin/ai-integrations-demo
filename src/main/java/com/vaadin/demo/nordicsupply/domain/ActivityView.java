package com.vaadin.demo.nordicsupply.domain;

/**
 * The views that write to the activity log, by the names its {@code view_name} column holds, each with what its model
 * may read besides the prompt.
 */
public enum ActivityView {
    DASHBOARD(
            "Dashboard",
            "The database schema, today's date, this widget's chat, query and any chart configuration; never a row"),
    CLAIM_FORM(
            "Claims",
            "The attached message, the claim form, and the customers, contacts, orders and shipments it looked up");

    private final String label;
    private final String modelCouldSee;

    ActivityView(String label, String modelCouldSee) {
        this.label = label;
        this.modelCouldSee = modelCouldSee;
    }

    /** The view's name in the rail. */
    public String label() {
        return label;
    }

    /** What the view's model may read besides the prompt, in a sentence, as the log records it with every turn. */
    public String modelCouldSee() {
        return modelCouldSee;
    }
}
