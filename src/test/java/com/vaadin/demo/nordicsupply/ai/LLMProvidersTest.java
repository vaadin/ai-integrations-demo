package com.vaadin.demo.nordicsupply.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A chat's provider is a Spring AI client with a memory of its own: a restored chat's turns are in it before the
 * model is asked anything, and every turn is sent with that chat's turns and no other's. The model is a stub that
 * answers "Done.".
 */
class LLMProvidersTest {

    private final ChatModel model = mock(ChatModel.class);
    private final LLMProviders providers = new LLMProviders(ChatClient.builder(model));

    @BeforeEach
    void theModelAnswersDone() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var done = new ChatResponse(List.of(new Generation(new AssistantMessage("Done."))));
        when(model.call(any(Prompt.class))).thenReturn(done);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(done));
    }

    private static ChatMessage said(ChatMessage.Role role, String text) {
        return new ChatMessage(role, text, null, null);
    }

    private static List<String> texts(List<? extends Message> messages) {
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

    /** The next question is answered in context: the model is sent the restored turns before it, and no other chat's. */
    @Test
    void aQuestionIsSentAfterTheChatsRestoredTurnsAndNoOthers() {
        var widget = providers.forChat(List.of(
                said(ChatMessage.Role.USER, "Late shipments last month"),
                said(ChatMessage.Role.ASSISTANT, "TITLE: Late shipments")));
        providers.forChat(List.of(said(ChatMessage.Role.USER, "Open claims")));

        ask(widget, "Only Finland");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(texts(prompt.getValue().getInstructions()))
                .containsExactly("Late shipments last month", "TITLE: Late shipments", "Only Finland");
    }

    /** A chat remembers its own turns: the second question is sent after the first and its answer. */
    @Test
    void aChatRemembersItsOwnTurns() {
        var chat = providers.forChat(List.of());
        var other = providers.forChat(List.of());

        ask(chat, "Open claims");
        ask(other, "Late shipments");
        ask(chat, "Only Finland");

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(texts(prompts.getValue().getInstructions())).containsExactly("Open claims", "Done.", "Only Finland");
    }

    /** The reply comes whole and off the request thread, so the chat shows the assistant typing meanwhile. */
    @Test
    void aChatsReplyComesWholeAndOffTheRequestThread() {
        assertThat(providers.forChat(List.of())).isInstanceOfSatisfying(SpringAILLMProvider.class, spring -> {
            assertThat(spring.isStreaming()).isFalse();
            assertThat(spring.isBackgroundExecution()).isTrue();
        });
    }

    @Test
    void aSingleTurnIsSentAlone() {
        var provider = providers.forSingleTurns();

        ask(provider, "The damaged pallet");
        ask(provider, "The missing cartons");

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).stream(prompts.capture());
        assertThat(texts(prompts.getValue().getInstructions())).containsExactly("The missing cartons");
    }

    /** The model is shown the memory's window, the last twenty messages; the message list keeps the rest. */
    @Test
    void aLongChatIsRememberedByTheMemorysWindow() {
        var history = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> said(i % 2 == 1 ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, "message " + i))
                .toList();
        var chat = providers.forChat(history);

        ask(chat, "message 41");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(texts(prompt.getValue().getInstructions()))
                .containsExactlyElementsOf(IntStream.rangeClosed(21, 41)
                        .mapToObj(i -> "message " + i)
                        .toList());
    }
}
