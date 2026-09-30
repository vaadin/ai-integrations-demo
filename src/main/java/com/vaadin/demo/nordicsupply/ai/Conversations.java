package com.vaadin.demo.nordicsupply.ai;

import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Where the application meets the model. Every chat on screen is one conversation with its own id and its own
 * memory; this class hands each of them an {@link LLMProvider} built on a Spring AI {@link ChatClient}.
 * <p>
 * The client comes from Spring AI's auto-configured {@link ChatClient.Builder}, so the model, its options and the
 * provider (OpenAI by default, an OpenAI-compatible server with the {@code local} profile) are chosen in
 * {@code application.properties}. One base client carries a {@link MessageChatMemoryAdvisor} over Spring AI's
 * auto-configured {@link ChatMemory}, a window of recent messages that all conversations share, keyed by conversation
 * id; each conversation's client is derived from it with {@code mutate()} and its own id as the advisor's default
 * parameter, since the Vaadin provider makes the calls and cannot pass the id per call as the Spring AI reference
 * shows. Because the application owns that memory, restoring a saved conversation means writing its turns into the
 * memory here; the Vaadin orchestrator restores the message list on screen from the same turns, and the provider's own
 * {@code setHistory} does nothing.
 * <p>
 * Extension points, each one line where it applies:
 * <ul>
 * <li>Memory that outlives the JVM, or a wider window: replace the {@link ChatMemory} bean, or only its repository,
 * for example with {@code spring-ai-starter-model-chat-memory-repository-jdbc} (which ships schema scripts for
 * PostgreSQL, MySQL, SQL Server, HSQLDB and Oracle; H2 needs one of its own); nothing here changes.</li>
 * <li>Retrieval, guardrails, logging: add advisors next to the memory advisor on the base client.</li>
 * <li>Different options per conversation, such as a cheaper model for a widget chat: {@code defaultOptions} on the
 * derived builder in {@link #restore}.</li>
 * </ul>
 */
@Component
public class Conversations {

    private final ChatClient base;
    private final ChatMemory memory;

    public Conversations(ChatClient.Builder clients, ChatMemory memory) {
        this.memory = memory;
        this.base = clients.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    /** An id for a conversation that starts now. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** A provider for a conversation with no history yet. */
    public LLMProvider start(String conversationId) {
        return restore(conversationId, List.of());
    }

    /**
     * Empties a conversation's memory, so that its next turn is its first: for a conversation whose turns stand
     * alone, such as a form filled from one message after another, where an earlier message would only be sent again
     * and read into the next.
     */
    public void forget(String conversationId) {
        memory.clear(conversationId);
    }

    /**
     * A provider for a conversation whose earlier turns are known: they become the model's memory, so the next
     * question is answered in context. Pass the same turns to {@code AIOrchestrator.Builder.withHistory} for the
     * message list.
     */
    public LLMProvider restore(String conversationId, List<ChatMessage> history) {
        memory.clear(conversationId);
        if (!history.isEmpty()) {
            memory.add(
                    conversationId,
                    history.stream().map(Conversations::toMessage).toList());
        }
        var client = base.mutate()
                .defaultAdvisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .build();
        return new SpringAILLMProvider(client);
    }

    private static Message toMessage(ChatMessage turn) {
        return turn.role() == ChatMessage.Role.USER
                ? new UserMessage(turn.content())
                : new AssistantMessage(turn.content());
    }
}
