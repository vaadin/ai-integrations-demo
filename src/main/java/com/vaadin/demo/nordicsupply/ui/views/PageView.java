package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.component.html.Main;

import com.vaadin.demo.nordicsupply.ui.components.PageHeading;

/**
 * A page of the back office: the document's {@code main} landmark, filling the content area, with the heading every
 * page carries at its top. A view extends it and adds its content after the heading.
 */
public abstract class PageView extends Main {

    protected PageView(String pageClassName, String title, String subtitle, String watermark) {
        addClassNames("page", pageClassName);
        add(new PageHeading(title, subtitle, watermark));
    }
}
