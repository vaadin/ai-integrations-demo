package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridNoneSelectionModel;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import com.vaadin.demo.nordicsupply.ai.Conversations;
import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider.ToolCall;
import com.vaadin.demo.nordicsupply.ai.TurnLog;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.Budgets;
import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;
import com.vaadin.demo.nordicsupply.domain.WidgetType;
import com.vaadin.demo.nordicsupply.ui.TestUi;

/**
 * A widget asks its question with the demo's instructions and the demo's date, takes the model's title and
 * description for itself, and shows the rest in its chat. A saved widget comes back with its chat and its query.
 */
class InsightWidgetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ScriptedProvider model = new ScriptedProvider(
            "TITLE: Late shipments\nSUMMARY: Shipments after the promised date.\n\nCounted from late_shipments.");
    private final Conversations conversations = mock(Conversations.class);

    /** Where the widgets' turns are recorded, as the Dashboard view hands it to them. */
    private final ActivityLog log = mock(ActivityLog.class);

    private final TurnLog turns =
            new TurnLog(log, new ActivityLog.Author(UUID.randomUUID(), 8), ActivityView.DASHBOARD);

    /**
     * Conversations over a real memory, with a model that writes each turn into it as the memory advisor does: the
     * question when the turn starts, the answer when it ends. The answers are queued by the test.
     */
    private final InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();

    private final MessageWindowChatMemory memory =
            MessageWindowChatMemory.builder().chatMemoryRepository(repository).build();
    private final Deque<Supplier<Flux<String>>> answers = new ArrayDeque<>();
    private final Conversations remembering = new Conversations(ChatClient.builder(mock(ChatModel.class)), memory) {
        @Override
        public LLMProvider restore(String conversationId, List<ChatMessage> history) {
            super.restore(conversationId, history); // writes the history into the memory
            return new LLMProvider() {
                @Override
                public Flux<String> stream(LLMRequest request) {
                    memory.add(conversationId, List.of(new UserMessage(request.userMessage())));
                    var written = new StringBuilder();
                    return answers.remove()
                            .get()
                            .doOnNext(written::append)
                            .doOnComplete(() ->
                                    memory.add(conversationId, List.of(new AssistantMessage(written.toString()))));
                }

                @Override
                public void setHistory(List<ChatMessage> history, Map<String, List<AIAttachment>> attachments) {}
            };
        }
    };
    private final JdbcDatabaseProvider db = new JdbcDatabaseProvider(() -> null, new PackData());
    private final AIBudget budget = Budgets.budget();

    private final UI ui = TestUi.withSession();

    /** A small database of weekly counts per country, for the widgets whose model calls the tools. */
    private static EmbeddedDatabase weekly;

    @BeforeAll
    static void weeklyCounts() {
        weekly = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("insightwidgettest")
                .build();
        var jdbc = new JdbcTemplate(weekly);
        jdbc.execute("CREATE TABLE weekly (country VARCHAR(20), wk VARCHAR(3), n INT)");
        jdbc.update(
                "INSERT INTO weekly VALUES ('Finland', 'W35', 4), ('Finland', 'W36', 6), ('Estonia', 'W35', 1), ('Estonia', 'W36', 2)");
        jdbc.execute("CREATE TABLE orders (id INT, order_number VARCHAR(10), customer_id INT)");
        jdbc.update("INSERT INTO orders VALUES (1, 'SO-1', 7), (2, 'SO-2', 8)");
    }

    @AfterAll
    static void closed() {
        weekly.shutdown();
    }

    @BeforeEach
    void aModel() {
        UI.setCurrent(ui);
        when(conversations.restore(anyString(), anyList())).thenReturn(model);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    /** A widget on the page, so that the model's reply reaches it. */
    private InsightWidget widget(WidgetType type) {
        var widget = new InsightWidget(
                type, "Late shipments last month", db, conversations, budget, "Lars", List.of(), turns);
        ui.add(widget);
        return widget;
    }

    /** A widget on the page whose questions the given budget admits. */
    private InsightWidget widget(WidgetType type, LLMProvider provider, AIBudget admitting) {
        var own = mock(Conversations.class);
        when(own.restore(anyString(), anyList())).thenReturn(provider);
        var widget = new InsightWidget(type, "Late shipments last month", db, own, admitting, "Lars", List.of(), turns);
        ui.add(widget);
        return widget;
    }

    /** A widget whose model calls the tools, on the database of weekly counts. */
    private InsightWidget widget(WidgetType type, ScriptedProvider scripted) {
        var own = mock(Conversations.class);
        when(own.restore(anyString(), anyList())).thenReturn(scripted);
        var widget = new InsightWidget(
                type,
                "Orders per week",
                new JdbcDatabaseProvider(() -> new JdbcTemplate(weekly), new PackData()),
                own,
                budget,
                "Lars",
                List.of(),
                turns);
        ui.add(widget);
        return widget;
    }

    private static Grid<?> gridOf(InsightWidget widget) {
        return (Grid<?>) widget.getContent()
                .getChildren()
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private static Configuration chartOf(InsightWidget widget) {
        return widget.getContent()
                .getChildren()
                .filter(Chart.class::isInstance)
                .map(Chart.class::cast)
                .findFirst()
                .orElseThrow()
                .getConfiguration();
    }

    /** Vaadin Charts draws with Highcharts' own light theme unless styled mode is on; the demo's CSS colours the chart. */
    @Test
    void aChartIsDrawnInTheThemesColours() {
        assertThat(chartOf(widget(WidgetType.CHART)).getChart().getStyledMode()).isTrue();
    }

    /** Several series are told apart by a legend; one series needs none, the widget's title names it. */
    @Test
    void aChartHasALegendExactlyWhenItHasSeveralSeries() {
        var perCountry = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: One series per country.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT country AS _series, wk AS category, n AS amount FROM weekly\"]}")));
        var finland = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Finland alone.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'\"]}")));

        perCountry.ask("Orders per week, one series per country");
        finland.ask("Orders per week in Finland");

        assertThat(chartOf(perCountry).getSeries()).hasSize(2);
        assertThat(chartOf(perCountry).getLegend().getEnabled()).isTrue();
        assertThat(chartOf(finland).getSeries()).hasSize(1);
        assertThat(chartOf(finland).getLegend().getEnabled()).isFalse();
    }

    /** The legend follows the number of series unless the user asked for it or against it, which the model then set. */
    @Test
    void aLegendTheUserAskedForOrAgainstIsKept() {
        var perCountry = "{\"queries\":[\"SELECT country AS _series, wk AS category, n AS amount FROM weekly\"]}";
        var finland = "{\"queries\":[\"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'\"]}";
        var hidden = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: One series per country.",
                        new ToolCall("update_chart_data_source", perCountry),
                        new ToolCall(
                                "update_chart_configuration", "{\"configuration\":{\"legend\":{\"enabled\":false}}}")));
        var shown = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Finland alone.",
                        new ToolCall("update_chart_data_source", finland),
                        new ToolCall(
                                "update_chart_configuration", "{\"configuration\":{\"legend\":{\"enabled\":true}}}")));

        hidden.ask("Orders per week by country, without a legend");
        shown.ask("Orders per week in Finland, with a legend");

        assertThat(chartOf(hidden).getSeries()).hasSize(2);
        assertThat(chartOf(hidden).getLegend().getEnabled()).isFalse();
        assertThat(chartOf(shown).getSeries()).hasSize(1);
        assertThat(chartOf(shown).getLegend().getEnabled()).isTrue();
    }

    /**
     * The chat would otherwise show the model's narration and the two lines word by word until the turn ends; the
     * widget shows the wait itself, and the turn still runs off the request thread.
     */
    @Test
    void aSpringAiProviderAnswersWholeAndOffTheRequestThread() {
        var spring = new SpringAILLMProvider(
                ChatClient.builder(mock(ChatModel.class)).build());
        when(conversations.restore(anyString(), anyList())).thenReturn(spring);

        widget(WidgetType.GRID);

        assertThat(spring.isStreaming()).isFalse();
        assertThat(spring.isBackgroundExecution()).isTrue();
    }

    /** The rows are the model's answer, not a list to pick from. */
    @Test
    void aGridsRowsCannotBeSelected() {
        var grid = widget(WidgetType.GRID)
                .getContent()
                .getChildren()
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertThat(((Grid<?>) grid).getSelectionModel()).isInstanceOf(GridNoneSelectionModel.class);
    }

    /** Highcharts would print "Values" beside an axis nobody titled; the model's own title for it is kept. */
    @Test
    void aChartsYAxisIsUntitledUnlessTheModelTitledIt() {
        var query = "{\"queries\":[\"SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk\"]}";
        var untitled = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Orders.", new ToolCall("update_chart_data_source", query)));
        var titled = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Orders.",
                        new ToolCall("update_chart_data_source", query),
                        new ToolCall(
                                "update_chart_configuration",
                                "{\"configuration\":{\"yAxis\":{\"title\":{\"text\":\"Orders\"}}}}")));

        untitled.ask("Orders per week");
        titled.ask("Orders per week, the axis titled Orders");

        assertThat(chartOf(untitled).getyAxis().getTitle().getText()).isEmpty();
        assertThat(chartOf(titled).getyAxis().getTitle().getText()).isEqualTo("Orders");
    }

    /** The widget carries the title, so a title the model gives the chart itself is taken off after every turn. */
    @Test
    void aTitleTheModelGivesTheChartIsTakenOffAgain() {
        var titling = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Orders per week.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk\"]}"),
                        new ToolCall(
                                "update_chart_configuration",
                                "{\"configuration\":{\"chart\":{\"type\":\"column\"},\"title\":{\"text\":\"Orders\"}}}")));

        titling.ask("Orders per week, with a chart title");
        titling.ask("And again");

        assertThat(chartOf(titling).getChart().getType()).isEqualTo(ChartType.COLUMN);
        assertThat(chartOf(titling).getTitle().getText()).isEmpty();
    }

    @Test
    void aQuestionGoesToTheModelWithTheDemosInstructionsAndTheDemosDate() {
        var widget = widget(WidgetType.GRID);

        widget.ask("Late shipments last month");

        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.userMessage()).isEqualTo("Late shipments last month");
            assertThat(request.systemPrompt())
                    .contains("TITLE:")
                    .contains("Never state a number")
                    .contains("This widget is a table and stays one");
            // the session context, which the providers add to the user message for the model
            assertThat(request.sessionContext())
                    .contains("Current date and time: 2026-09-07T")
                    .contains("(Monday, Europe/Helsinki)");
        });
        assertThat(widget.chat().messageList().getItems())
                .extracting(MessageListItem::getUserName, MessageListItem::getText)
                .containsExactly(
                        tuple("Lars", "Late shipments last month"),
                        tuple(InsightWidget.ASSISTANT_NAME, "Counted from late_shipments."));
        assertThat(widget.getTitle()).isEqualTo("Late shipments");
        assertThat(widget.description()).isEqualTo("Shipments after the promised date.");
    }

    /**
     * Every question is admitted by the budget before it is a request: one over it is answered by the chat with the
     * reason, and nothing reaches the model.
     */
    @Test
    void aQuestionOverTheBudgetIsAnsweredByTheChatAndNeverReachesTheModel() {
        var widget = widget(WidgetType.GRID, model, Budgets.withAddress(new Address(1, 3)));

        widget.ask("Late shipments last month");
        widget.ask("And the month before");

        assertThat(model.requests()).hasSize(1);
        assertThat(widget.chat().messageList().getItems().getLast())
                .extracting(MessageListItem::getUserName, MessageListItem::getText)
                .containsExactly(
                        InsightWidget.ASSISTANT_NAME,
                        "You have reached today's limit of 1 questions; the AI is available to you again tomorrow.");
        assertThat(progressOf(widget).isVisible()).isFalse();
    }

    /** A turn holds one of its caller's slots while it runs, and gives it back when it ends, answered or failed. */
    @Test
    void aTurnGivesItsSlotBackWhenItEndsAnsweredOrFailed() {
        var oneAtATime = Budgets.withAddress(new Address(120, 1));
        var answered = widget(WidgetType.GRID, model, oneAtATime);
        answered.ask("Late shipments last month");
        answered.ask("And the month before");
        assertThat(model.requests()).hasSize(2);

        var failing = new ScriptedProvider("TITLE: Late shipments\nSUMMARY: Shipments after the promised date.") {
            private boolean failed;

            @Override
            public Flux<String> stream(LLMRequest request) {
                var answer = super.stream(request);
                if (!failed) {
                    failed = true;
                    return Flux.error(new IllegalStateException("The model is down"));
                }
                return answer;
            }
        };
        var recovered = widget(WidgetType.GRID, failing, oneAtATime);
        recovered.ask("Late shipments last month");
        recovered.ask("Late shipments last month");

        assertThat(failing.requests()).hasSize(2);
    }

    /**
     * Every question is a paid model call, so a widget's chat takes a sentence or two: a longer question is answered by
     * the chat and never reaches the model, and one at the cap is asked.
     */
    @Test
    void aQuestionOverTheCapIsAnsweredByTheChatAndNeverReachesTheModel() {
        var widget = widget(WidgetType.GRID);

        widget.ask("x".repeat(501));

        assertThat(model.requests()).isEmpty();
        assertThat(widget.chat().messageList().getItems())
                .extracting(MessageListItem::getUserName, item -> item.getText().length())
                .containsExactly(tuple("Lars", 501), tuple(InsightWidget.ASSISTANT_NAME, 61));
        assertThat(widget.chat().messageList().getItems().getLast().getText())
                .isEqualTo("A question can have at most 500 characters; this one has 501.");
        assertThat(progressOf(widget).isVisible()).isFalse();

        widget.ask("x".repeat(500));

        assertThat(model.requests())
                .singleElement()
                .satisfies(request -> assertThat(request.userMessage()).hasSize(500));
    }

    @Test
    void aChartWidgetIsToldItIsAChart() {
        widget(WidgetType.CHART).ask("Orders per month");

        assertThat(model.requests().getFirst().systemPrompt()).contains("This widget is a chart and stays one");
    }

    /** A model that gives the next of its replies on every turn. */
    private static LLMProvider replying(String... replies) {
        var next = new ArrayDeque<>(List.of(replies));
        return new LLMProvider() {
            @Override
            public Flux<String> stream(LLMRequest request) {
                return Flux.just(next.poll());
            }

            @Override
            public void setHistory(List<ChatMessage> history, Map<String, List<AIAttachment>> attachments) {}
        };
    }

    /** With no note, the chat shows the summary; a reply without the two lines leaves the widget's name as it was. */
    @Test
    void aReplyWithoutANoteShowsTheSummaryAndOneWithoutLinesChangesNothing() {
        when(conversations.restore(anyString(), anyList()))
                .thenReturn(replying("TITLE: Open claims\nSUMMARY: Claims not yet resolved.", "Here it is."));
        var widget = widget(WidgetType.GRID);

        widget.ask("Open claims");
        assertThat(widget.chat().messageList().getItems().getLast().getText()).isEqualTo("Claims not yet resolved.");

        widget.ask("Show it again");
        assertThat(widget.getTitle()).isEqualTo("Open claims");
        assertThat(widget.description()).isEqualTo("Claims not yet resolved.");
        assertThat(widget.chat().messageList().getItems().getLast().getText()).isEqualTo("Here it is.");
    }

    /**
     * A turn can end with no word from the model, after its tools have changed the widget. The chat says so, instead
     * of leaving the empty bubble the orchestrator opened for the answer; the widget keeps its name.
     */
    @Test
    void aTurnThatEndsWithoutAReplyIsSaidSoInTheChat() {
        when(conversations.restore(anyString(), anyList()))
                .thenReturn(replying("TITLE: Open claims\nSUMMARY: Claims not yet resolved.", "", " \n"));
        var widget = widget(WidgetType.GRID);
        widget.ask("Open claims");

        widget.ask("Sort by days open");
        assertThat(widget.chat().messageList().getItems().getLast().getText())
                .isEqualTo("The model wrote no reply. If the widget did not change as you asked, ask again.");

        widget.ask("Sort it the other way");
        assertThat(widget.chat().messageList().getItems().getLast().getText())
                .isEqualTo("The model wrote no reply. If the widget did not change as you asked, ask again.");
        assertThat(widget.getTitle()).isEqualTo("Open claims");
        assertThat(widget.description()).isEqualTo("Claims not yet resolved.");
    }

    /**
     * A widget runs its query again on the connection its provider gives at that moment, as after a change of the
     * country filter, and asks the model nothing.
     */
    @Test
    void aRerunRunsTheQueryAgainOnTheConnectionOfTheMoment() {
        var finland = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("insightwidgettest-finland")
                .build();
        try {
            var finnish = new JdbcTemplate(finland);
            finnish.execute("CREATE TABLE weekly (country VARCHAR(20), wk VARCHAR(3), n INT)");
            finnish.update("INSERT INTO weekly VALUES ('Finland', 'W36', 6)");
            var connection = new AtomicReference<>(new JdbcTemplate(weekly));
            var widget = new InsightWidget(
                    WidgetType.CHART,
                    "Orders per week",
                    new JdbcDatabaseProvider(connection::get, new PackData()),
                    conversations,
                    budget,
                    "Lars",
                    List.of(),
                    turns);
            ui.add(widget);
            widget.restore("SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk", null);
            assertThat(chartOf(widget).getxAxis().getCategories()).containsExactly("W35", "W36");

            connection.set(finnish);
            widget.rerun();

            assertThat(chartOf(widget).getxAxis().getCategories()).containsExactly("W36");
            assertThat(model.requests()).isEmpty();
        } finally {
            finland.shutdown();
        }
    }

    /** The model is asked not to select raw ids; the widget hides any that come anyway, and shows the rest. */
    @Test
    void rawIdColumnsAreHiddenAndTheOthersShown() {
        var widget = widget(
                WidgetType.GRID,
                new ScriptedProvider(
                        "TITLE: Orders\nSUMMARY: The orders.",
                        new ToolCall(
                                "update_grid_data",
                                "{\"query\":\"SELECT id, order_number AS \\\"Order\\\", customer_id,"
                                        + " id AS \\\"Customer.ID\\\" FROM orders\"}")));

        widget.ask("Orders with their keys");

        var grid = (Grid<?>) widget.getContent()
                .getChildren()
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(grid.getColumns())
                .extracting(Grid.Column::getKey, Grid.Column::isVisible)
                .containsExactlyInAnyOrder(
                        tuple("ID", false),
                        tuple("Order", true),
                        tuple("CUSTOMER_ID", false),
                        tuple("Customer.ID", false));
    }

    /**
     * A turn that fails ends the wait, and its question does not stay in the memory to be answered again with the
     * next one. The error notification is only on the page when Flow writes a response; the manual cases
     * check it.
     */
    @Test
    void aFailedTurnEndsAndItsQuestionIsLeftOutOfTheMemory() {
        answers.add(() -> Flux.just("TITLE: Late shipments\nSUMMARY: Shipments after the date."));
        answers.add(() -> Flux.error(new IllegalStateException("401: Incorrect API key provided")));
        var widget = rememberingWidget();

        widget.ask("Late shipments last month");
        widget.ask("And by carrier");

        assertThat(remembered())
                .containsExactly(
                        "Late shipments last month", "TITLE: Late shipments\nSUMMARY: Shipments after the date.");
        assertThat(progressOf(widget).isVisible()).isFalse();
        assertThat(widget.getTitle()).isEqualTo("Late shipments");
    }

    /** A model that answers when the test says so. */
    private static final class Waiting implements LLMProvider {
        private final Sinks.Many<String> answer = Sinks.many().unicast().onBackpressureBuffer();

        @Override
        public Flux<String> stream(LLMRequest request) {
            return flux();
        }

        Flux<String> flux() {
            return answer.asFlux();
        }

        @Override
        public void setHistory(List<ChatMessage> history, Map<String, List<AIAttachment>> attachments) {}

        void answer(String text) {
            answer.tryEmitNext(text);
            answer.tryEmitComplete();
        }
    }

    /**
     * While a turn runs, the widget shows the wait with its progress bar, where the answer will appear, and the chat's
     * input stays enabled, so it keeps the focus. The answer ends the wait.
     */
    @Test
    void whileATurnRunsTheWidgetShowsTheWaitAndTheInputStaysEnabled() {
        var waiting = new Waiting();
        when(conversations.restore(anyString(), anyList())).thenReturn(waiting);
        var widget = widget(WidgetType.GRID);

        widget.ask("Late shipments last month");

        assertThat(progressOf(widget).isVisible()).isTrue();
        assertThat(widget.chat().messageInput().isEnabled()).isTrue();

        waiting.answer("TITLE: Late shipments\nSUMMARY: Shipments after the promised date.");

        assertThat(progressOf(widget).isVisible()).isFalse();
    }

    /** The bar that shows the wait on the widget itself, where the answer will appear. */
    private static ProgressBar progressOf(InsightWidget widget) {
        return (ProgressBar) widget.getContent()
                .getChildren()
                .filter(ProgressBar.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /** Removed from the dashboard or gone with the page, a widget's conversation is let go. */
    @Test
    void aWidgetThatLeavesThePageLeavesNothingInTheMemory() {
        answers.add(() -> Flux.just("TITLE: Late shipments\nSUMMARY: Shipments after the date."));
        var widget = rememberingWidget();
        widget.ask("Late shipments last month");
        assertThat(remembered()).hasSize(2);

        ui.remove(widget);

        assertThat(repository.findConversationIds()).isEmpty();
    }

    /** The memory advisor writes a turn when it ends, so a turn that outlives its widget must not leave it behind. */
    @Test
    void aTurnThatEndsAfterItsWidgetLeftLeavesNothingInTheMemoryEither() {
        var waiting = new Waiting();
        answers.add(waiting::flux);
        var widget = rememberingWidget();
        widget.ask("Late shipments last month");

        ui.remove(widget);
        waiting.answer("TITLE: Late shipments\nSUMMARY: Shipments after the date.");

        assertThat(repository.findConversationIds()).isEmpty();
    }

    /** A saved chat as the orchestrator keeps it: the question, and the model's reply with its two lines. */
    private static List<ChatMessage> savedChat() {
        var asked = LocalDateTime.of(2026, 9, 7, 9, 11, 20)
                .atZone(DemoClock.CLOCK.getZone())
                .toInstant();
        return List.of(
                new ChatMessage(ChatMessage.Role.USER, "Late shipments last month", null, asked),
                new ChatMessage(
                        ChatMessage.Role.ASSISTANT,
                        "TITLE: Late shipments\nSUMMARY: Shipments after the promised date.\n\nCounted per week.",
                        null,
                        asked.plusSeconds(40)));
    }

    /**
     * A saved widget comes back with its chat, shown the way the live one was, and the model remembers it: a
     * follow-up is asked in the conversation the chat shows, and the chat saved next time holds both.
     */
    @Test
    void aSavedWidgetsChatIsShownAndRemembered() {
        answers.add(() -> Flux.just("TITLE: Late shipments by carrier\nSUMMARY: Per carrier.\n\nGrouped by carrier."));
        var widget = new InsightWidget(
                WidgetType.GRID, "Late shipments", db, remembering, budget, "Lars", savedChat(), turns);
        ui.add(widget);

        assertThat(widget.chat().messageList().getItems())
                .extracting(MessageListItem::getUserName, MessageListItem::getText)
                .containsExactly(
                        tuple("Lars", "Late shipments last month"),
                        tuple(InsightWidget.ASSISTANT_NAME, "Counted per week."));
        assertThat(remembered())
                .containsExactly(
                        "Late shipments last month",
                        "TITLE: Late shipments\nSUMMARY: Shipments after the promised date.\n\nCounted per week.");

        widget.ask("And by carrier");

        assertThat(remembered()).hasSize(4).startsWith("Late shipments last month");
        assertThat(widget.history())
                .extracting(ChatMessage::content)
                .containsExactly(
                        "Late shipments last month",
                        "TITLE: Late shipments\nSUMMARY: Shipments after the promised date.\n\nCounted per week.",
                        "And by carrier",
                        "TITLE: Late shipments by carrier\nSUMMARY: Per carrier.\n\nGrouped by carrier.");
    }

    /**
     * Two tabs showing the same saved widget restore its chat into two memories, so that removing one widget, or
     * closing its tab, leaves the other's conversation as it was.
     */
    @Test
    void twoWidgetsRestoredFromOneSavedChatKeepTheirOwnMemories() {
        var first = new InsightWidget(
                WidgetType.GRID, "Late shipments", db, remembering, budget, "Lars", savedChat(), turns);
        var second = new InsightWidget(
                WidgetType.GRID, "Late shipments", db, remembering, budget, "Lars", savedChat(), turns);
        ui.add(first, second);

        assertThat(repository.findConversationIds()).hasSize(2);

        ui.remove(first);

        assertThat(repository.findConversationIds())
                .singleElement()
                .satisfies(id -> assertThat(repository.findByConversationId(id)).hasSize(2));
    }

    /** A question whose turn failed is not saved with the chat, as the model's memory left it out too. */
    @Test
    void aQuestionThatGotNoReplyIsNotSaved() {
        answers.add(() -> Flux.just("TITLE: Late shipments\nSUMMARY: Shipments after the date."));
        answers.add(() -> Flux.error(new IllegalStateException("429: Rate limit reached")));
        var widget = rememberingWidget();

        widget.ask("Late shipments last month");
        widget.ask("And by carrier");

        assertThat(widget.history())
                .extracting(ChatMessage::role, ChatMessage::content)
                .containsExactly(
                        tuple(ChatMessage.Role.USER, "Late shipments last month"),
                        tuple(ChatMessage.Role.ASSISTANT, "TITLE: Late shipments\nSUMMARY: Shipments after the date."));
    }

    /**
     * The components stamp a chat message with the system clock; the widget dates it on the dataset's day, at the
     * time it was written, in the chat and in what is saved, as the seeded chats are dated.
     */
    @Test
    void theChatIsDatedOnTheDatasetsDay() {
        var widget = widget(WidgetType.GRID);

        widget.ask("Late shipments last month");

        var zone = DemoClock.CLOCK.getZone();
        assertThat(widget.chat().messageList().getItems())
                .hasSize(2)
                .extracting(item -> item.getTime().atZone(zone).toLocalDate())
                .containsOnly(LocalDate.of(2026, 9, 7));
        assertThat(widget.history())
                .hasSize(2)
                .extracting(turn -> turn.time().atZone(zone).toLocalDate())
                .containsOnly(LocalDate.of(2026, 9, 7));
    }

    /**
     * Every turn goes to the activity log: the question, what the model could see, its answer, and the query it
     * proposed.
     */
    @Test
    void aChartsTurnIsRecordedWithTheQueriesTheModelProposed() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(7L);
        var widget = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Finland alone.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'\"]}")));

        widget.ask("Orders per week in Finland");

        verify(log)
                .asked(
                        any(),
                        eq(ActivityView.DASHBOARD),
                        eq("Orders per week in Finland"),
                        contains("chart configuration; never a row"));
        verify(log).ended(eq(7L), startsWith("TITLE: Orders per week"), any(), any(), any());
        verify(log).proposed(7L, "SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'");
    }

    /**
     * A new chart's first turn proposes its queries and the settings the model chose, without the number Vaadin Charts
     * gives every axis.
     */
    @Test
    void aNewChartsFirstTurnIsRecordedWithItsQueriesAndTheModelsSettings() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(11L);
        var query = "SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk";
        var widget = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Orders.",
                        new ToolCall("update_chart_data_source", "{\"queries\":[\"" + query + "\"]}"),
                        new ToolCall(
                                "update_chart_configuration",
                                "{\"configuration\":{\"chart\":{\"type\":\"line\"},\"xAxis\":{\"title\":{\"text\":\"Week\"}}}}")));

        widget.ask("Orders per week as a line");

        var proposal = ArgumentCaptor.forClass(String.class);
        verify(log).proposed(eq(11L), proposal.capture());
        assertThat(proposal.getValue()).startsWith(query + "\n\n");
        assertThat(JSON.readTree(proposal.getValue().substring(query.length() + 2)))
                .isEqualTo(JSON.readTree("{\"chart\":{\"type\":\"line\"},\"xAxis\":{\"title\":{\"text\":\"Week\"}}}"));
    }

    /**
     * A turn that only changes how a saved chart is drawn proposes the settings it changed, not the queries it left as
     * they were. A dashboard the user saved keeps the chart's styled mode, which the change of type resets in what the
     * model reads; the model did not choose that, and the widget keeps the chart itself in styled mode.
     */
    @Test
    void aChartsTurnThatChangesItsLookAloneIsRecordedWithTheConfiguration() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(9L);
        var widget = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: As a line.",
                        new ToolCall(
                                "update_chart_configuration", "{\"configuration\":{\"chart\":{\"type\":\"line\"}}}")));
        widget.restore(
                "SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk",
                "{\"configuration\":{\"chart\":{\"type\":\"column\",\"styledMode\":true}}}");

        widget.ask("Make it a line chart instead");

        var proposal = ArgumentCaptor.forClass(String.class);
        verify(log).proposed(eq(9L), proposal.capture());
        assertThat(JSON.readTree(proposal.getValue())).isEqualTo(JSON.readTree("{\"chart\":{\"type\":\"line\"}}"));
    }

    /** A turn that leaves a chart as it was proposes nothing, and the log keeps the model's answer. */
    @Test
    void aChartsTurnThatChangesNothingIsRecordedWithItsAnswer() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(10L);
        var query = "SELECT wk AS category, SUM(n) AS amount FROM weekly GROUP BY wk";
        var widget = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: As it was.",
                        new ToolCall("update_chart_data_source", "{\"queries\":[\"" + query + "\"]}")));
        widget.restore(query, null);

        widget.ask("Show it again");

        verify(log).ended(eq(10L), startsWith("TITLE: Orders per week"), any(), any(), any());
        verify(log, never()).proposed(anyLong(), anyString());
    }

    @Test
    void aTablesTurnIsRecordedWithTheQueryTheModelProposed() {
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(8L);
        var widget = widget(
                WidgetType.GRID,
                new ScriptedProvider(
                        "TITLE: Orders\nSUMMARY: The orders.",
                        new ToolCall("update_grid_data", "{\"query\":\"SELECT order_number FROM orders\"}")));

        widget.ask("Our orders");

        verify(log).proposed(8L, "SELECT order_number FROM orders");
    }

    /** A refused question never reaches the model; the log keeps the rule, and as much of it as a question may have. */
    @Test
    void aQuestionOverTheCapIsRecordedAsRefusedWithItsRule() {
        var widget = widget(WidgetType.GRID);

        widget.ask("x".repeat(Budgets.DEFAULTS.maxQuestionLength() + 100));

        verify(log)
                .refused(
                        any(),
                        eq(ActivityView.DASHBOARD),
                        eq("x".repeat(Budgets.DEFAULTS.maxQuestionLength()) + "…"),
                        eq("A question can have at most 500 characters; this one has 600."));
        verify(log, never()).asked(any(), any(), anyString(), anyString());
    }

    /**
     * A question typed into the chat is dated the same way, and so is one the chat refuses for its length, which never
     * becomes a turn: every message of the chat reads as written on the dataset's day.
     */
    @Test
    void aTypedQuestionAndARefusedOneAreDatedOnTheDatasetsDayToo() {
        var widget = widget(WidgetType.GRID);
        var input = widget.chat().messageInput();

        ComponentUtil.fireEvent(input, new MessageInput.SubmitEvent(input, true, "Late shipments last month"));
        widget.ask("x".repeat(501));
        ComponentUtil.fireEvent(input, new MessageInput.SubmitEvent(input, true, "y".repeat(501)));

        var zone = DemoClock.CLOCK.getZone();
        assertThat(widget.chat().messageList().getItems())
                .hasSize(6)
                .extracting(item -> item.getTime().atZone(zone).toLocalDate())
                .containsOnly(LocalDate.of(2026, 9, 7));
    }

    /**
     * A visitor far from Helsinki reads the chat on the dataset's day too, at their own time of day. The two zones lie
     * 14 hours behind Helsinki and 11 ahead, so a chat dated in Helsinki's zone would read another day in one of them,
     * whatever the hour the test runs.
     */
    @Test
    void theChatIsDatedOnTheDatasetsDayInTheVisitorsTimeZone() {
        for (var zone : List.of(ZoneId.of("Pacific/Pago_Pago"), ZoneId.of("Pacific/Kiritimati"))) {
            TestUi.inTimeZone(ui, zone.getId());
            var widget = widget(
                    WidgetType.GRID,
                    new ScriptedProvider("TITLE: Late shipments\nSUMMARY: Shipments after the promised date."));

            widget.ask("Late shipments last month");

            assertThat(widget.chat().messageList().getItems())
                    .as(zone.getId())
                    .hasSize(2)
                    .extracting(item -> item.getTime().atZone(zone).toLocalDate())
                    .containsOnly(LocalDate.of(2026, 9, 7));
            assertThat(widget.history())
                    .as(zone.getId())
                    .hasSize(2)
                    .extracting(turn -> turn.time().atZone(zone).toLocalDate())
                    .containsOnly(LocalDate.of(2026, 9, 7));
        }
    }

    /** Every answered turn tells the view, which then offers to save; a failed turn changed nothing and does not. */
    @Test
    void anAnsweredTurnTellsTheViewAndAFailedOneDoesNot() {
        answers.add(() -> Flux.just("TITLE: Late shipments\nSUMMARY: Shipments after the date."));
        answers.add(() -> Flux.error(new IllegalStateException("429: Rate limit reached")));
        var changes = new ArrayList<InsightWidget>();
        var widget = rememberingWidget();
        widget.onChanged(changes::add);

        widget.ask("Late shipments last month");
        widget.ask("And by carrier");

        assertThat(changes).containsExactly(widget);
    }

    /** A widget that has shown no result has nothing to save; the view leaves it out. */
    @Test
    void aWidgetWithoutAResultHasNoState() {
        assertThat(widget(WidgetType.GRID, new ScriptedProvider("unused")).state())
                .isNull();
        assertThat(widget(WidgetType.CHART, new ScriptedProvider("unused")).state())
                .isNull();
    }

    /** A saved table runs its query again on the widget's connection, and hides the raw ids as a live one does. */
    @Test
    void aSavedTableRunsItsQueryAgain() {
        var widget = widget(WidgetType.GRID, new ScriptedProvider("unused"));

        widget.restore("SELECT id, order_number AS \"Order\" FROM orders", "{\"colspan\":2,\"rowspan\":3}");

        assertThat(gridOf(widget).getColumns())
                .extracting(Grid.Column::getKey, Grid.Column::isVisible)
                .containsExactlyInAnyOrder(tuple("ID", false), tuple("Order", true));
        assertThat(widget.getColspan()).isEqualTo(2);
        assertThat(widget.getRowspan()).isEqualTo(3);
        assertThat(widget.state())
                .isEqualTo(new InsightWidget.State(
                        "SELECT id, order_number AS \"Order\" FROM orders", "{\"colspan\":2,\"rowspan\":3}"));
    }

    /** A chart drawn from two queries, a series each, comes back from its state with both. */
    @Test
    void aChartOfTwoQueriesComesBackWithBoth() {
        var saved = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: Finland and Estonia.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'\","
                                        + " \"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Estonia'\"]}")));
        saved.ask("Finland and Estonia per week");
        var state = saved.state();
        var restored = widget(WidgetType.CHART, new ScriptedProvider("unused"));

        restored.restore(state.sql(), state.json());

        assertThat(chartOf(saved).getSeries()).hasSize(2);
        assertThat(chartOf(restored).getSeries()).hasSize(2);
    }

    /** A saved table whose query fails on the way back keeps it, so that saving the dashboard does not drop it. */
    @Test
    void aSavedTableWhoseQueryFailedKeepsIt() {
        var widget = widget(WidgetType.GRID, new ScriptedProvider("unused"));

        widget.restore("SELECT order_number FROM no_such_table", null);

        assertThat(gridOf(widget).getColumns()).isEmpty();
        assertThat(widget.state())
                .isEqualTo(new InsightWidget.State(
                        "SELECT order_number FROM no_such_table", "{\"colspan\":1,\"rowspan\":1}"));
    }

    /** A chart saved without a look, as the seeded ones are, is drawn as columns by the widget's rules. */
    @Test
    void aSavedChartWithoutALookIsDrawnAsColumns() {
        var widget = widget(WidgetType.CHART, new ScriptedProvider("unused"));

        widget.restore("SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'", null);

        var chart = chartOf(widget);
        assertThat(chart.getChart().getType()).isEqualTo(ChartType.COLUMN);
        assertThat(chart.getChart().getStyledMode()).isTrue();
        assertThat(chart.getSeries()).hasSize(1);
        assertThat(chart.getxAxis().getCategories()).containsExactly("W35", "W36");
        assertThat(chart.getLegend().getEnabled()).isFalse();
        assertThat(chart.getTitle().getText()).isEmpty();
    }

    /**
     * A chart's state carries its queries, its look and its size, and a widget restored from it draws the same chart.
     * The legend the widget had set is still the widget's, so it follows the series when a follow-up changes them.
     */
    @Test
    void aChartComesBackFromItsStateAsItWasSaved() {
        var saved = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week\nSUMMARY: One series per country.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT country AS _series, wk AS category, n AS amount FROM weekly\"]}"),
                        new ToolCall(
                                "update_chart_configuration", "{\"configuration\":{\"chart\":{\"type\":\"line\"}}}")));
        saved.ask("Orders per week by country, as lines");
        saved.setColspan(2);
        var state = saved.state();
        var restored = widget(
                WidgetType.CHART,
                new ScriptedProvider(
                        "TITLE: Orders per week in Finland\nSUMMARY: Finland alone.",
                        new ToolCall(
                                "update_chart_data_source",
                                "{\"queries\":[\"SELECT wk AS category, n AS amount FROM weekly WHERE country = 'Finland'\"]}")));

        restored.restore(state.sql(), state.json());

        assertThat(state.sql()).isEqualTo("SELECT country AS _series, wk AS category, n AS amount FROM weekly");
        assertThat(restored.getColspan()).isEqualTo(2);
        assertThat(chartOf(restored).getChart().getType()).isEqualTo(ChartType.LINE);
        assertThat(chartOf(restored).getChart().getStyledMode()).isTrue();
        assertThat(chartOf(restored).getSeries()).hasSize(2);
        assertThat(chartOf(restored).getLegend().getEnabled()).isTrue();

        restored.ask("Only Finland");

        assertThat(chartOf(restored).getSeries()).hasSize(1);
        assertThat(chartOf(restored).getLegend().getEnabled()).isFalse();
    }

    /** A widget whose conversation lives in a real memory. */
    private InsightWidget rememberingWidget() {
        var widget = new InsightWidget(
                WidgetType.GRID, "Late shipments last month", db, remembering, budget, "Lars", List.of(), turns);
        ui.add(widget);
        return widget;
    }

    /** What the memory holds of the one conversation the test's widget started, oldest first. */
    private List<String> remembered() {
        var ids = repository.findConversationIds();
        return ids.isEmpty()
                ? List.of()
                : repository.findByConversationId(ids.getFirst()).stream()
                        .map(Message::getText)
                        .toList();
    }
}
