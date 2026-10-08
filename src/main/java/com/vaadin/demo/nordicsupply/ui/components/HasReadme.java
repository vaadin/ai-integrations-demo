package com.vaadin.demo.nordicsupply.ui.components;

import java.io.Serializable;
import java.util.List;

/**
 * A view with a readme: what it tells a visitor about itself, in Markdown, shown under the view's page title the
 * first time the view is visited in a session and whenever Help is pressed. Each readme describes its view on its
 * own, since the visitor sees one view at a time. A view built on documented components names their documentation
 * as "Read more" links under the text.
 */
public interface HasReadme {

    /** A documentation page: the link's text, its address, and a note on what the page has for this view. */
    record Link(String text, String href, String note) implements Serializable {}

    record ReadmeSection(String summary, String content) implements Serializable {}

    String readme();

    /** The documentation behind the view; empty for a view that has none to point at. */
    default List<Link> readMore() {
        return List.of();
    }

    default List<ReadmeSection> readmeSections() {
        return List.of();
    }
}
