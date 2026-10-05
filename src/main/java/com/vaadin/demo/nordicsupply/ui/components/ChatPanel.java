package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/** A message list with an input under it: the chat every widget and view owns. */
public class ChatPanel extends VerticalLayout {

    private final MessageList messageList = new MessageList();
    private final MessageInput messageInput = new MessageInput();

    public ChatPanel() {
        addClassName("chat-panel");
        messageList.setMarkdown(true);
        messageList.setSizeFull();
        messageInput.setWidthFull();
        setPadding(false);
        setSpacing(false);
        setSizeFull();
        add(messageList, messageInput);
        expand(messageList);
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
