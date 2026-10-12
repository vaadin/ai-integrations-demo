package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

/** A page's own actions sit at the heading's end, in the row the stylesheet spaces and aligns. */
class PageHeadingTest {

    @Test
    void thePagesActionsSitInTheRowTheStylesheetLaysOut() {
        var heading = new PageHeading("Dashboard", "Understand Your Operations", "Dashboard");
        var save = new Button("Save Dashboard");

        heading.addActions(new Span("All countries"), save);

        var actions = save.getParent().orElseThrow();
        assertThat(actions.getElement().getClassList()).contains("page-actions");
        assertThat(actions.getElement().getAttribute("slot")).isEqualTo("suffix");
        assertThat(actions.getChildren()).hasSize(2);
    }
}
