package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;

/**
 * A view's readme, anchored to the Help button in the application drawer: the view's title, the Markdown, and the
 * documentation the view links to as "Read more", each link opening in a new tab so the demo stays where it is. A
 * readme without links has no "Read more". The links are the popover's own anchors rather than Markdown links, since
 * {@code vaadin-markdown} strips a link's target.
 */
public class ReadmePopover extends Popover {

    private final H2 title = new H2();
    private final Markdown body = new Markdown();
    private final H3 readMoreTitle = new H3("Read more");
    private final UnorderedList readMore = new UnorderedList();

    public ReadmePopover() {
        var close = new Button("Close", event -> close());
        close.addThemeVariants(ButtonVariant.PRIMARY);
        add(title, body, readMoreTitle, readMore, close);
        setWidth("30rem");
        setPosition(PopoverPosition.END_TOP);
        setModal(false);
        addThemeVariants(PopoverVariant.ARROW);
        addClassNames("help");
    }

    /** Sets the readme, the title of the view it belongs to, and the documentation the view links to. */
    public void setReadme(String title, String readme, List<HasReadme.Link> links) {
        this.title.setText(title);
        setAriaLabel(title + " help");
        body.setContent(readme);
        readMoreTitle.setVisible(!links.isEmpty());
        readMore.setVisible(!links.isEmpty());
        readMore.removeAll();
        for (var link : links) {
            var anchor = new Anchor(link.href(), link.text());
            anchor.setTarget(AnchorTarget.BLANK);
            readMore.add(new ListItem(anchor, new Text(": " + link.note())));
        }
    }

    /** Sets and opens the readme. */
    public void show(String title, String readme, List<HasReadme.Link> links) {
        setReadme(title, readme, links);
        open();
    }
}
