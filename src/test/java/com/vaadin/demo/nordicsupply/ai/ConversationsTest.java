package com.vaadin.demo.nordicsupply.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A conversation's provider is a Spring AI client whose memory the application fills: a restored chat's turns are
 * in the memory before the model is asked anything, and every turn is sent with its own conversation's memory. The
 * model is a stub that answers "Done.".
 */
class ConversationsTest {

    private final ChatModel model = mock(ChatModel.class);
    private final InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
    /** The window Spring AI's auto-configured memory has, over the test's own repository. */
    private final Conversations conversations = new Conversations(
            ChatClient.builder(model),
            MessageWindowChatMemory.builder().chatMemoryRepository(repository).build());

    @BeforeEach
    void theModelAnswersDone() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("Done."))))));
    }

    private static ChatMessage said(ChatMessage.Role role, String text) {
        return new ChatMessage(role, text, null, null);
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::getText).toList();
    }

    private static void ask(LLMProvider provider, String question) {
        provider.stream(new LLMProvider.LLMRequest() {
                    @Override
                    public String userMessage() {
                        return question;
                    }

                    @Override
                    public List<AIAttachment> attachments() {
                        return List.of();
                    }

                    @Override
                    public String systemPrompt() {
                        return null;
                    }

                    @Override
                    public Object[] tools() {
                        return new Object[0];
                    }
                })
                .blockLast();
    }

    @Test
    void aRestoredConversationsTurnsAreTheModelsMemory() {
        var provider = conversations.restore(
                "widget-1",
                List.of(
                        said(ChatMessage.Role.USER, "Late shipments last month"),
                        said(ChatMessage.Role.ASSISTANT, "TITLE: Late shipments")));

        assertThat(provider).isInstanceOf(SpringAILLMProvider.class);
        assertThat(repository.findByConversationId("widget-1"))
                .extracting(Message::getText, Message::getClass)
                .containsExactly(
                        tuple("Late shipments last month", UserMessage.class),
                        tuple("TITLE: Late shipments", AssistantMessage.class));
    }

    /** The next question is answered in context: the model is sent the restored turns before it. */
    @Test
    void aTurnSendsTheModelItsConversationsMemoryAndKeepsTheExchange() {
        var widget = conversations.restore(
                "widget-1",
                List.of(
                        said(ChatMessage.Role.USER, "Late shipments last month"),
                        said(ChatMessage.Role.ASSISTANT, "TITLE: Late shipments")));
        conversations.restore("widget-2", List.of(said(ChatMessage.Role.USER, "Open claims")));

        ask(widget, "Only Finland");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        assertThat(texts(prompt.getValue().getInstructions()))
                .containsExactly("Late shipments last month", "TITLE: Late shipments", "Only Finland");
        assertThat(texts(repository.findByConversationId("widget-1")))
                .containsExactly("Late shipments last month", "TITLE: Late shipments", "Only Finland", "Done.");
        assertThat(texts(repository.findByConversationId("widget-2"))).containsExactly("Open claims");
    }

    @Test
    void aNewConversationStartsWithAnEmptyMemoryOfItsOwn() {
        conversations.restore("widget-1", List.of(said(ChatMessage.Role.USER, "Late shipments last month")));

        ask(conversations.start("widget-2"), "Open claims");
        ask(conversations.start("widget-3"), "Late shipments");

        assertThat(texts(repository.findByConversationId("widget-2"))).containsExactly("Open claims", "Done.");
        assertThat(texts(repository.findByConversationId("widget-3"))).containsExactly("Late shipments", "Done.");
        assertThat(texts(repository.findByConversationId("widget-1"))).containsExactly("Late shipments last month");
    }

    @Test
    void aForgottenConversationsNextTurnIsItsFirst() {
        var provider = conversations.start("claims-1");
        ask(provider, "The damaged pallet");

        conversations.forget("claims-1");
        ask(provider, "The missing cartons");

        assertThat(texts(repository.findByConversationId("claims-1"))).containsExactly("The missing cartons", "Done.");
    }

    @Test
    void restoringAgainReplacesWhatTheMemoryHeld() {
        conversations.restore("widget-1", List.of(said(ChatMessage.Role.USER, "Late shipments last month")));

        conversations.restore("widget-1", List.of(said(ChatMessage.Role.USER, "Open claims")));

        assertThat(repository.findByConversationId("widget-1"))
                .extracting(Message::getText)
                .containsExactly("Open claims");
    }

    /** The model is shown the memory's window, the last twenty messages; the message list keeps the rest. */
    @Test
    void aLongConversationIsRememberedByTheMemorysWindow() {
        var history = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> said(i % 2 == 1 ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, "message " + i))
                .toList();

        conversations.restore("widget-1", history);

        assertThat(texts(repository.findByConversationId("widget-1")))
                .containsExactlyElementsOf(IntStream.rangeClosed(21, 40)
                        .mapToObj(i -> "message " + i)
                        .toList());
    }

    @Test
    void everyConversationGetsAnIdOfItsOwn() {
        assertThat(Conversations.newId()).isNotEqualTo(Conversations.newId()).hasSize(36);
    }
}
