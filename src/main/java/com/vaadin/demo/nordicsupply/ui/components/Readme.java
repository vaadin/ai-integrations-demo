package com.vaadin.demo.nordicsupply.ui.components;

import java.io.Serializable;

/**
 * What a view tells a visitor about itself: a title and the text in Markdown, shown in a dialog the first time the
 * view is visited in a session and whenever Help is pressed. Each readme describes its view on its own, since the
 * visitor sees one view at a time.
 */
public record Readme(String title, String body) implements Serializable {}
