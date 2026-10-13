package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * A line saying what the chat is about, a message list and an input under it: the chat every widget and view owns.
 */
public class ChatPanel extends VerticalLayout {

    /** What the chat is about, in business terms; hidden while empty. */
    private final Span intro = new Span();

    private final MessageList messageList = new MessageList();
    private final MessageInput messageInput = new MessageInput();

    public ChatPanel() {
        addClassName("chat-panel");
        intro.addClassName("chat-intro");
        messageList.setMarkdown(true);
        messageList.setSizeFull();
        messageInput.setWidthFull();
        setPadding(false);
        setSpacing(false);
        setSizeFull();
        add(intro, messageList, messageInput);
        expand(messageList);
    }

    /** Says what the chat is about, over the messages; empty shows nothing. */
    public void setIntro(String text) {
        intro.setText(text);
    }

    /** The list the orchestrator writes the turns into. */
    public MessageList messageList() {
        return messageList;
    }

    /** The input the orchestrator reads the user's message from. */
    public MessageInput messageInput() {
        return messageInput;
    }
}
