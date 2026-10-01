package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;

import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.orchestrator.AIController;
import com.vaadin.flow.component.ai.orchestrator.RequestListener;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A chart widget's controller as the model reaches it: the chart controller, except that its state tool leaves out
 * what the chart took from the rows. In Vaadin 25.3.0-beta3 {@code get_chart_state} returns the configuration the
 * chart was drawn with, whose x-axis categories and series names are values from the query result, and the model
 * reads that state at the start of every chart turn. Vaadin's own fix (flow-components #10222) returns only what the
 * model set; with it this class goes.
 */
final class ChartStateWithoutRowValues implements AIController {

    private static final String STATE_TOOL = "get_chart_state";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChartAIController controller;

    ChartStateWithoutRowValues(ChartAIController controller) {
        this.controller = controller;
    }

    @Override
    public List<LLMProvider.ToolSpec> getTools() {
        return controller.getTools().stream()
                .map(tool -> tool.getName().equals(STATE_TOOL) ? withoutRowValues(tool) : tool)
                .toList();
    }

    @Override
    public void onRequest(RequestListener.RequestEvent event) {
        controller.onRequest(event);
    }

    @Override
    public void onResponse(ResponseListener.ResponseEvent event) {
        controller.onResponse(event);
    }

    private static LLMProvider.ToolSpec withoutRowValues(LLMProvider.ToolSpec tool) {
        return new LLMProvider.ToolSpec() {
            @Override
            public String getName() {
                return tool.getName();
            }

            @Override
            public String getDescription() {
                return tool.getDescription();
            }

            @Override
            public String getParametersSchema() {
                return tool.getParametersSchema();
            }

            @Override
            public String execute(JsonNode arguments) {
                var result = tool.execute(arguments);
                if (!result.startsWith("{")) {
                    return result; // the tool's own error text, which carries no state
                }
                var state = JSON.readTree(result);
                if (state.get("configuration") instanceof ObjectNode configuration) {
                    configuration.findParents("categories").forEach(axis -> ((ObjectNode) axis).remove("categories"));
                    if (configuration.get("series") != null) {
                        configuration.get("series").forEach(series -> ((ObjectNode) series).remove("name"));
                    }
                }
                return state.toString();
            }
        };
    }
}
