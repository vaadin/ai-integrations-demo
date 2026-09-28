package com.vaadin.demo.nordicsupply.ui.components;

/**
 * A view with a readme: what it tells a visitor about itself, in Markdown, shown under the view's page title the
 * first time the view is visited in a session and whenever Help is pressed. Each readme describes its view on its
 * own, since the visitor sees one view at a time.
 */
public interface HasReadme {

    String readme();
}
