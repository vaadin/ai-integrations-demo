package com.vaadin.demo.nordicsupply.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

/**
 * A model for the UI tests: it calls the tools it is given, in order, as a model would during a turn, answers every
 * prompt with the same text, and keeps the requests it was sent.
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
            var tool = request.explicitTools().stream()
                    .filter(candidate -> candidate.getName().equals(call.name()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no tool " + call.name()));
            tool.execute(JSON.readTree(call.arguments()));
        }
        return Flux.just(reply);
    }

    @Override
    public void setHistory(List<ChatMessage> history, Map<String, List<AIAttachment>> attachmentsByMessageId) {
        // the application owns the memory; nothing to restore here
    }

    /** The requests the orchestrator sent, oldest first. */
    public List<LLMRequest> requests() {
        return requests;
    }
}
