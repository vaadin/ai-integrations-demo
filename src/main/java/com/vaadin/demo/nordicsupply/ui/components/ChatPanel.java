package com.vaadin.demo.nordicsupply.ui.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListI18n;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.messages.MessageListItemVariant;
import com.vaadin.flow.component.messages.MessageListTypingIndicatorType;
import com.vaadin.flow.component.messages.MessageListVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * A line saying what the chat is about, a message list and an input under it: the chat every widget and view owns.
 */
public class ChatPanel extends VerticalLayout {

    /** What the chat is about, in business terms; hidden while empty. */
    private final Span intro = new Span();

    private final MessageList messageList = new MessageList(){
    @Override
    public void addItem(MessageListItem item) {
        var fromAssistant = InsightWidget.ASSISTANT_NAME.equals(item.getUserName());
        item.addThemeVariants(fromAssistant ? MessageListItemVariant.FULL_WIDTH : MessageListItemVariant.SELF);
        super.addItem(item);
    }
};
    private final MessageInput messageInput = new MessageInput();

    public ChatPanel() {
        addClassName("chat-panel");
        intro.addClassName("chat-intro");
        messageList.setMarkdown(true);
        messageList.setSizeFull();
        messageList.addThemeVariants(MessageListVariant.BUBBLE, MessageListVariant.ONE_TO_ONE);
        messageList.setTypingIndicatorType(MessageListTypingIndicatorType.MINIMAL);
        messageList.setI18n(new MessageListI18n().setTypingIndicatorText("working..."));
        messageInput.getElement().setAttribute("autofocus", "");
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
