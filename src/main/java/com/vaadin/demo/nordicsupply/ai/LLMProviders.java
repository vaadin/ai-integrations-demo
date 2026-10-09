package com.vaadin.demo.nordicsupply.ai;

import java.util.List;

import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Where the application meets the model: an {@link LLMProvider} for every orchestrator, built on a Spring AI
 * {@link ChatClient}. The client comes from Spring AI's auto-configured builder, so the model, its options and the
 * tool-call limits are set in {@code application.properties}, and the {@code local} profile can point it at an
 * OpenAI-compatible server. {@code new SpringAILLMProvider(chatModel)} would be shorter, but builds a client of its
 * own that those properties do not reach.
 * <p>
 * A chat that goes on, such as a widget's, needs the model to remember its earlier turns: {@link #forChat} gives it
 * a client with a {@link ChatMemory} of its own, holding the turns so far. A form filled from one message after
 * another has no such chat, and {@link #forSingleTurns} gives it a client without memory.
 */
@Component
public class LLMProviders {

    /** The id a chat's memory keeps its one conversation under; the memory advisor needs one. */
    private static final String CHAT = "chat";

    private final ChatClient.Builder clients;

    public LLMProviders(ChatClient.Builder clients) {
        this.clients = clients;
    }

    /**
     * A provider for a chat that goes on. The turns so far become the model's memory, so the next question is
     * answered in context; pass the same turns to {@code AIOrchestrator.Builder.withHistory} for the chat on screen.
     * The reply comes whole and off the request thread: the chat shows the assistant typing meanwhile, instead of
     * the model's narration word by word.
     *
     * @param history the chat so far, oldest first; empty for a chat that starts now
     * @return a provider with a memory of its own
     */
    public LLMProvider forChat(List<ChatMessage> history) {
        var memory = MessageWindowChatMemory.builder().build();
        if (!history.isEmpty()) {
            memory.add(CHAT, history.stream().map(LLMProviders::toMessage).toList());
        }
        var client = clients.clone()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultAdvisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, CHAT))
                .build();
        var provider = new SpringAILLMProvider(client);
        provider.setStreaming(false);
        provider.setBackgroundExecution(true);
        return provider;
    }

    /**
     * A provider whose every turn stands alone, with no memory of the turns before it: for a form filled from one
     * message after another, where an earlier message would only be sent again and read into the next.
     *
     * @return a provider without memory
     */
    public LLMProvider forSingleTurns() {
        return new SpringAILLMProvider(clients.clone().build());
    }

    private static Message toMessage(ChatMessage turn) {
        return turn.role() == ChatMessage.Role.USER
                ? new UserMessage(turn.content())
                : new AssistantMessage(turn.content());
    }
}
