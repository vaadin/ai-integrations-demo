package com.vaadin.demo.nordicsupply.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.chart.ChartConfigurationParser;
import com.vaadin.flow.component.ai.chart.ChartState;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.grid.AIDataRow;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.ai.grid.GridState;
import com.vaadin.flow.component.ai.orchestrator.AIController;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.orchestrator.RequestInterceptor;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.util.ChartSerialization;
import com.vaadin.flow.component.dashboard.DashboardWidget;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.messages.MessageInputI18n;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.function.SerializableConsumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.json.JsonMapper;

import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.ai.TurnLog;
import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.domain.WidgetType;

/**
 * One Dashboard widget: a grid or a chart with its own chat and orchestrator. The chat is shown in a popover beside
 * the widget while it is selected. The model names the widget during a turn through a tool of the widget's own,
 * {@link #nameWidget}: a short title and a one-sentence description in business terms. The model sees the schema text,
 * today's date, the widget's query and, for a chart, its configuration, and never a row. A saved widget comes back
 * with its chat, which the model is given as its memory, and with its query run anew.
 */
public class InsightWidget extends DashboardWidget {

    /** How the chat names the model's turns. */
    public static final String ASSISTANT_NAME = "Assistant";

    /** The longest question a chat takes: a sentence or two, since every question is a paid model call. */
    public static final int MAX_QUESTION_LENGTH = 500;

    /** Long enough to read the notice of a failed turn before it disappears. */
    private static final int ERROR_NOTIFICATION_MS = 8000;

    /**
     * What a visitor is told when a turn fails. The provider's own message can name the key; the orchestrator writes
     * it, with the exception, to the server log.
     */
    private static final String FAILED = "The model could not answer. Try again; the details are in the server log.";

    /** What the chat input suggests: the kinds of change a widget takes. */
    private static final String CHAT_PLACEHOLDER =
            "Change the chart type, add or remove a column, narrow the period, or ask which data this is based on";

    private static final String SYSTEM_PROMPT = """
            You help an analyst answer questions about live operations by querying the database described by the schema
            tool. Always call the tools; never invent data. Read the schema first if you have not. When a question can be
            read in more than one way, say which reading you used in one sentence.
            Once the widget shows the data, call name_widget with the widget's title and description; call it again when
            the user asks to rename the widget. Then reply with at most two short sentences: the reading you used, and
            what to check. Nothing else.
            Never state a number, a count or an average in your reply: the rows never reach you, only the widget shows
            them. Say what the widget shows, not what it contains.
            For tables: at most seven columns. Put the main entity's business key first (order_number, claim_number,
            customer_number, sku or shipment_number), prefer names to ids, and never select raw id columns.
            When grouping by day, week or month, select the period as a DATE (cast a truncated timestamp to DATE) or
            as a short label such as 'W34' or '2026-08', never as a timestamp.
            For charts: one row per point, the label column first and then one numeric column. For several series,
            add a column aliased _series that names each row's series. At most thirty points. Give the axes short
            titles and never set a chart title: the widget has its own.
            This widget is a %s and stays one. Only when the user asks for a %s, say in one sentence that the New
            Query box creates one beside this widget; otherwise do not mention it.
            The data you see is already limited to the countries the user may see; do not add country conditions
            unless the user asks for a specific country.
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * The keys of the saved state: the widget's size, and a chart's queries, its look, and the legend setting the
     * widget made itself, which a restored chart otherwise could not tell from one the user asked for.
     */
    private static final String COLSPAN = "colspan";

    private static final String ROWSPAN = "rowspan";

    private static final String QUERIES = "queries";

    private static final String CONFIGURATION = "configuration";

    private static final String LEGEND_OF_THE_WIDGET = "legendOfTheWidget";

    /** What is saved of a widget: the query it runs and, as JSON, its size and, for a chart, its queries and look. */
    public record State(String sql, String json) {}

    private final WidgetType type;
    private final Grid<AIDataRow> grid;
    private final GridAIController gridController;
    private final Chart chart;
    private final ChartAIController chartController;
    private final ChatPanel chat = new ChatPanel();

    /** The model's one-sentence description of the widget, in business terms; shown over its chat. */
    private String description = "";

    private final Button ask = new Button("Edit", new SvgIcon("icons/sparkle.svg"));
    private final ProgressBar progress = new ProgressBar();
    private final AIOrchestrator orchestrator;

    /** The legend setting the widget last gave the chart; any other setting on the chart is the model's. */
    private Boolean legendOfTheWidget;

    /**
     * The clock the chat is dated by: the dataset's day in the visitor's time zone, so that the chat reads that day
     * wherever the visitor is; the model's own "now" stays Helsinki's.
     */
    private final DemoClock chatClock = DemoClock.forVisitor(UI.getCurrent());

    /** The query a saved table was restored with; saved again if it did not run then. */
    private String restoredSql;

    private SerializableConsumer<InsightWidget> changeListener = widget -> {};

    /**
     * @param type whether the widget answers with a chart or a table; it keeps that kind
     * @param title the widget's title until the model names it: the question that made it, or the saved title
     * @param db the model's way into the data, on the signed-in user's connection
     * @param provider the model, with the widget's chat so far as its memory
     * @param userName how the chat names the user's turns
     * @param history the chat so far, oldest first: empty for a new widget, and for a saved one its saved chat, which
     *     the chat shows and the model remembers
     * @param turns where the widget's turns are recorded: the question, the query the model proposed, the answer
     */
    public InsightWidget(
            WidgetType type,
            String title,
            JdbcDatabaseProvider db,
            LLMProvider provider,
            String userName,
            List<ChatMessage> history,
            TurnLog turns) {
        super(title);
        this.type = type;
        addClassName("insight-widget");
        Component visual;
        AIController controller;
        if (type == WidgetType.CHART) {
            chart = new Chart();
            chart.setSizeFull();
            styled(chart.getConfiguration());
            chartController = new ChartAIController(chart, db);
            chartController.addStateChangeListener(state -> redraw());
            turns.watch(chartController);
            grid = null;
            gridController = null;
            controller = chartController;
            visual = chart;
        } else {
            grid = new Grid<>();
            // the rows are the model's answer, not a list to pick from
            grid.setSelectionMode(Grid.SelectionMode.NONE);
            grid.setSizeFull();
            gridController = new GridAIController(grid, db);
            gridController.addStateChangeListener(state -> hideIdColumns());
            turns.watch(gridController);
            chart = null;
            chartController = null;
            controller = gridController;
            visual = grid;
        }
        var builder = AIOrchestrator.builder(provider, systemPrompt(type))
                .withMessageList(chat.messageList())
                .withInput(chat.messageInput())
                .withController(controller)
                // the widget is the model's tool for naming it
                .withTools(this)
                .withMetadata(DemoClock::sessionContext)
                .withRequestInterceptor(turns.refusing(InsightWidget::refuseALongQuestion))
                // the wait shows where the answer will appear, also while the chat is closed
                .withRequestListener(turns.asking(event -> progress.setVisible(true)))
                .withResponseListener(turns.ending(this::onResponse))
                .withUserName(userName)
                .withAssistantName(ASSISTANT_NAME);
        if (!history.isEmpty()) {
            // the chat and the orchestrator's own record; the provider holds the model's memory
            builder.withHistory(history, Map.of());
        }
        orchestrator = builder.build();
        // the orchestrator adds a question and the place of its answer, or a refused question and the reason, while it
        // handles the input; this listener comes after its own, so the new messages are there to be dated
        chat.messageInput().addSubmitListener(event -> keepTheDemosDate());
        chat.messageInput()
                .setI18n(new MessageInputI18n().setMessage(CHAT_PLACEHOLDER).setSend("Send"));
        ask.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        ask.setAriaLabel("Edit this widget");
        ask.addClassName("widget-ask");
        setHeaderContent(ask);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.addClassName("widget-progress");
        var content = new VerticalLayout(progress, visual);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(false);
        content.expand(visual);
        setContent(content);
    }

    /** The system prompt for a widget of the given type. */
    private static String systemPrompt(WidgetType type) {
        return type == WidgetType.CHART
                ? SYSTEM_PROMPT.formatted("chart", "table")
                : SYSTEM_PROMPT.formatted("table", "chart");
    }

    /** A question over the cap is answered by the chat, before it is a request: nothing is sent to the model. */
    private static void refuseALongQuestion(RequestInterceptor.RequestInterceptEvent event) {
        var length = event.getUserMessage().length();
        if (length > MAX_QUESTION_LENGTH) {
            event.reject(
                    "A question can have at most " + MAX_QUESTION_LENGTH + " characters; this one has " + length + ".");
        }
    }

    /** The widget's chat; the view shows it in a popover while the widget is selected. */
    public ChatPanel chat() {
        return chat;
    }

    /** Whether the widget shows a table or a chart; it keeps its kind. */
    public WidgetType type() {
        return type;
    }

    /** The model's one-sentence description of what the widget shows; empty until the model has written one. */
    public String description() {
        return description;
    }

    /** The widget's description, as it was saved or as the model gives it: shown over the widget's chat. */
    public void setDescription(String description) {
        this.description = description == null ? "" : description;
        chat.setIntro(this.description);
    }

    /** Called when the user asks for this widget's chat from the button in its header. */
    public void onAsk(SerializableConsumer<InsightWidget> handler) {
        ask.addClickListener(e -> handler.accept(this));
    }

    /** Called after every answered turn, which may have changed the widget or its name; not after a restore. */
    public void onChanged(SerializableConsumer<InsightWidget> listener) {
        changeListener = listener;
    }

    /** Sends a question to this widget's chat. The widget has to be on the page: a reply is applied to a widget that is. */
    public void ask(String question) {
        orchestrator.prompt(question);
        keepTheDemosDate();
    }

    /**
     * The chat so far, oldest first, as it is saved: every question with its reply, dated on the dataset's day in the
     * visitor's time zone. A question that got no reply, because its turn failed or ended without a word, is left out.
     */
    public List<ChatMessage> history() {
        var turns = orchestrator.getHistory();
        var saved = new ArrayList<ChatMessage>(turns.size());
        for (int i = 0; i < turns.size(); i++) {
            var turn = turns.get(i);
            boolean answered = turn.role() == ChatMessage.Role.ASSISTANT
                    || (i + 1 < turns.size() && turns.get(i + 1).role() == ChatMessage.Role.ASSISTANT);
            if (answered) {
                var time = turn.time() == null ? null : chatClock.onTheDatasetsDay(turn.time());
                saved.add(new ChatMessage(turn.role(), turn.content(), turn.messageId(), time));
            }
        }
        return saved;
    }

    /**
     * What is saved of the widget, or {@code null} while it has shown no result: its query, its size and, for a
     * chart, its queries and its look, which the chart controller's state carries. A saved table whose query did not
     * run when it was restored keeps that query, so that saving the dashboard does not drop it.
     */
    public State state() {
        var json = JSON.createObjectNode();
        json.put(COLSPAN, getColspan());
        json.put(ROWSPAN, getRowspan());
        if (gridController != null) {
            var shown = gridController.getState();
            var sql = shown == null ? restoredSql : shown.query();
            return sql == null ? null : new State(sql, json.toString());
        }
        var shown = chartController.getState();
        if (shown == null) {
            return null;
        }
        var queries = json.putArray(QUERIES);
        shown.queries().forEach(queries::add);
        json.set(CONFIGURATION, JSON.readTree(ChartSerialization.toJSON(shown.configuration())));
        if (legendOfTheWidget != null) {
            json.put(LEGEND_OF_THE_WIDGET, legendOfTheWidget);
        }
        return new State(String.join("\n\n", shown.queries()), json.toString());
    }

    /**
     * Shows a saved widget again: its size, and its query run anew on the signed-in user's connection, through the
     * controller's own restore; nothing is sent to the model. A chart saved without a look, as the seeded ones are,
     * is drawn as columns.
     */
    public void restore(String sql, String stateJson) {
        var json = stateJson == null || stateJson.isBlank() ? JSON.createObjectNode() : JSON.readTree(stateJson);
        setColspan(Math.max(1, json.path(COLSPAN).asInt(1)));
        setRowspan(Math.max(1, json.path(ROWSPAN).asInt(1)));
        if (gridController != null) {
            restoredSql = sql;
            gridController.restoreState(new GridState(sql));
            // the controller's restore does not tell its state listeners
            hideIdColumns();
            return;
        }
        var queries = new ArrayList<String>();
        json.path(QUERIES).forEach(query -> queries.add(query.asString()));
        if (queries.isEmpty()) {
            queries.add(sql);
        }
        var configuration = json.has(CONFIGURATION)
                ? ChartConfigurationParser.parse(json.get(CONFIGURATION).toString())
                : columns();
        legendOfTheWidget =
                json.has(LEGEND_OF_THE_WIDGET) ? json.get(LEGEND_OF_THE_WIDGET).asBoolean() : null;
        chartController.restoreState(new ChartState(queries, configuration));
        redraw();
    }

    /**
     * Runs the widget's query again on the connection its database provider gives now, as after a change of the
     * dashboard's country filter, through the same restore a saved widget takes; nothing is sent to the model. A
     * widget that has shown no result stays as it is.
     */
    public void rerun() {
        var shown = state();
        if (shown != null) {
            restore(shown.sql(), shown.json());
        }
    }

    /**
     * Dates the chat's messages on the dataset's day, at the visitor's time of day when they were written: the
     * components stamp a message with the system clock, and the demo's calendar stands on the dataset's day, where a
     * saved chat is dated too. Run after every question the orchestrator has handled, a refused one included, which
     * never becomes a turn.
     */
    private void keepTheDemosDate() {
        for (var item : chat.messageList().getItems()) {
            if (item.getTime() != null) {
                item.setTime(chatClock.onTheDatasetsDay(item.getTime()));
            }
        }
    }

    /**
     * After every render: hide raw id columns the model may still have selected. The renderer's own columns and
     * headers stay as they are; the rows belong to the controller and are not read here.
     */
    private void hideIdColumns() {
        for (var column : grid.getColumns()) {
            var key = column.getKey();
            if (key != null && isIdColumn(key)) {
                column.setVisible(false);
            }
        }
    }

    private static boolean isIdColumn(String key) {
        var k = key.toLowerCase(Locale.ROOT);
        return k.equals("id") || k.endsWith("_id") || k.endsWith(".id");
    }

    /**
     * Runs on the provider's thread: anything that touches the UI goes through {@code ui.access}, which a widget that
     * left the page while the turn ran no longer has.
     */
    private void onResponse(ResponseListener.ResponseEvent event) {
        getUI().ifPresent(ui -> ui.access(() -> progress.setVisible(false)));
        if (event.getError().isPresent()) {
            getUI().ifPresent(ui -> ui.access(() -> {
                var n = Notification.show(FAILED, ERROR_NOTIFICATION_MS, Notification.Position.BOTTOM_START);
                n.addThemeVariants(NotificationVariant.ERROR);
            }));
            return;
        }
        getUI().ifPresent(ui -> ui.access(() -> changeListener.accept(this)));
    }

    /**
     * The tool the model names the widget with, registered on the orchestrator with {@code withTools}: Spring AI
     * reads the method's name, its description and the parameters' descriptions into the tool definition the model is
     * sent. The model calls it during a turn, on the provider's thread, so the name is applied through
     * {@code ui.access}, as every change from that thread; a widget that left the page meanwhile keeps its name. The
     * title goes to the header and the description over the chat at once; the view hears of the change when the
     * turn ends.
     *
     * @param title the widget's new title
     * @param description what the widget shows, in one sentence
     * @return what the model is told back
     */
    @Tool(
            name = "name_widget",
            description = "Names the widget for the user: its title and a one-sentence description of what it shows."
                    + " Call it once the widget shows the data, and again when the user asks to rename the widget.")
    public String nameWidget(
            @ToolParam(description = "At most six words naming what the widget shows, e.g. Late shipments, August 2026")
                    String title,
            @ToolParam(
                            description =
                                    "One sentence in business terms saying what the widget contains and what was counted")
                    String description) {
        getUI().ifPresent(ui -> ui.access(() -> {
            setTitle(title);
            setDescription(description);
        }));
        return "The widget is now named " + title + ".";
    }

    /**
     * The widget's rules for a chart, applied after every render and after a restore: the model's configuration
     * replaces the widget's or merges into it, and a restored one is whatever was saved.
     */
    private void redraw() {
        styled(chart.getConfiguration());
        legend(chart.getConfiguration());
        chart.drawChart(true);
    }

    /** The look of a chart saved without one: columns, which suit the label and the number a seeded query selects. */
    private static Configuration columns() {
        var configuration = new Configuration();
        configuration.getChart().setType(ChartType.COLUMN);
        return configuration;
    }

    /**
     * One series needs no legend, it would only repeat the widget's title; several need one to tell them apart. That
     * is the widget's default: a legend the user asked the model to show or hide is kept, since only a setting the
     * widget made itself, or none, is replaced.
     */
    private void legend(Configuration configuration) {
        var legend = configuration.getLegend();
        if (legend.getEnabled() == null || legend.getEnabled().equals(legendOfTheWidget)) {
            legendOfTheWidget = configuration.getSeries().size() > 1;
            legend.setEnabled(legendOfTheWidget);
        }
    }

    /** Vaadin Charts draws with Highcharts' own light theme unless styled mode is on; the demo's CSS styles it. */
    private static void styled(Configuration configuration) {
        configuration.getChart().setStyledMode(true);
        // the widget carries the title; a title inside the chart would repeat it and take room from the plot
        configuration.setTitle("");
        // no axis title unless the model set one: Highcharts would otherwise print "Values"
        var yAxis = configuration.getyAxis();
        if (yAxis.getTitle() == null || yAxis.getTitle().getText() == null) {
            yAxis.setTitle("");
        }
    }
}
