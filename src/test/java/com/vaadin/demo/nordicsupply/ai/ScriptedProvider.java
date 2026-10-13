package com.vaadin.demo.nordicsupply.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

/**
 * A model for the UI tests: it calls the tools it is given, in order, as a model would during a turn, answers every
 * prompt with the same text, and keeps the requests it was sent. A tool is one of a controller's, or a {@code @Tool}
 * method of an object registered with {@code withTools}, called through Spring AI's own callback for it.
 */
public class ScriptedProvider implements LLMProvider {

    /** A tool the model calls, and its arguments as JSON. */
    public record ToolCall(String name, String arguments) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String reply;
    private final List<ToolCall> calls;
    private final List<LLMRequest> requests = new ArrayList<>();

    public ScriptedProvider(String reply, ToolCall... calls) {
        this.reply = reply;
        this.calls = List.of(calls);
    }

    @Override
    public Flux<String> stream(LLMRequest request) {
        requests.add(request);
        for (var call : calls) {
            request.explicitTools().stream()
                    .filter(candidate -> candidate.getName().equals(call.name()))
                    .findFirst()
                    .ifPresentOrElse(
                            tool -> tool.execute(JSON.readTree(call.arguments())),
                            () -> annotatedTool(request, call.name()).call(call.arguments()));
        }
        return Flux.just(reply);
    }

    private static ToolCallback annotatedTool(LLMRequest request, String name) {
        var callbacks = request.tools().length == 0
                ? new ToolCallback[0]
                : MethodToolCallbackProvider.builder()
                        .toolObjects(request.tools())
                        .build()
                        .getToolCallbacks();
        return Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no tool " + name));
    }

    @Override
    public void setHistory(List<ChatMessage> history, Map<String, List<AIAttachment>> attachmentsByMessageId) {
        // a restored chat is the provider's memory to keep; this one keeps none
    }

    /** The requests the orchestrator sent, oldest first. */
    public List<LLMRequest> requests() {
        return requests;
    }
}
