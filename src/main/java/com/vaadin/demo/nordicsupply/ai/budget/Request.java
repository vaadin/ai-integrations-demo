package com.vaadin.demo.nordicsupply.ai.budget;

import java.util.List;

import com.vaadin.flow.component.ai.common.AIAttachment;

/**
 * A request for a turn: who asks, from which feature, and what, the prompt with its attachments. The budget admits or
 * refuses it, and the usage log records it with what became of it.
 *
 * @param caller who asks
 * @param feature the feature asking, as the logs name it
 * @param prompt what the model is asked, or {@code null} where a message was refused before a prompt was made of it
 * @param attachments what goes with the prompt, of which the usage log keeps only the names and sizes
 */
public record Request(Caller caller, String feature, String prompt, List<AIAttachment> attachments) {}
