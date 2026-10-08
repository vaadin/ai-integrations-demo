package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
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

    private final Markdown body = new Markdown();
    private Accordion sections;
    private final UnorderedList readMoreLinks = new UnorderedList();
    private final AccordionPanel readMoreSection = new AccordionPanel("Read More", readMoreLinks);

    public ReadmePopover() {
        setPosition(PopoverPosition.END_BOTTOM);
        setModal(false);
        addThemeVariants(PopoverVariant.ARROW);
        addClassNames("help");
    }

    /** Sets the readme, the title of the view it belongs to, and the documentation the view links to. */
    public void setReadme(
            String title, String readme, List<HasReadme.ReadmeSection> readmeSections, List<HasReadme.Link> links) {
        setAriaLabel(title + " help");
        removeAll();

        body.setContent(readme);
        if (readme != null) {
            add(body);
        }

        sections = new Accordion();
        add(sections);

        for (var section : readmeSections) {
            sections.add(section.summary(), new Markdown(section.content()));
        }

        sections.add(readMoreSection);
        readMoreSection.setVisible(!links.isEmpty());
        readMoreSection.addClassName("read-more");
        readMoreLinks.removeAll();
        for (var link : links) {
            var anchor = new Anchor(link.href(), link.text());
            anchor.setTarget(AnchorTarget.BLANK);
            readMoreLinks.add(new ListItem(anchor, new Text(link.note())));
        }
    }

    /** Sets and opens the readme. */
    public void show(String title, String readme, List<HasReadme.ReadmeSection> sections, List<HasReadme.Link> links) {
        setReadme(title, readme, sections, links);
        open();
    }
}
