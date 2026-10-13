package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.dashboard.Dashboard;
import com.vaadin.flow.component.dashboard.DashboardItemMovedEvent;
import com.vaadin.flow.component.dashboard.DashboardItemRemoveEvent;
import com.vaadin.flow.component.dashboard.DashboardItemResizedEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import reactor.core.publisher.Flux;

import com.vaadin.demo.nordicsupply.ai.LLMProviders;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider.ToolCall;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;
import com.vaadin.demo.nordicsupply.ai.budget.Budgets;
import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.config.ScopedConnections;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityView;
import com.vaadin.demo.nordicsupply.domain.WidgetType;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.SavedDashboards;
import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.TestUi;
import com.vaadin.demo.nordicsupply.ui.components.AIPausedNotice;
import com.vaadin.demo.nordicsupply.ui.components.CountryFilter;
import com.vaadin.demo.nordicsupply.ui.components.InsightWidget;
import com.vaadin.demo.nordicsupply.ui.components.QueryPanel;

/**
 * The page opens with the saved dashboard, or with one prompt when there is none; every question becomes a widget in
 * front of the New Query tile, and Save Dashboard keeps what is on screen.
 */
class DashboardViewTest {

    private final LLMProviders providers = mock(LLMProviders.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private final ScopedConnections connections = mock(ScopedConnections.class);
    private final SavedDashboards dashboards = mock(SavedDashboards.class);
    private final ActivityLog log = mock(ActivityLog.class);
    private final Visit visit = new Visit(log);

    /** The session's country filter, as the session bean holds it for every tab. */
    private final SharedValueSignal<Scope> countries = new SharedValueSignal<>(Scopes.FI_EE);

    /** Every model the widgets were given, to tell whether any was asked something. */
    private final List<ScriptedProvider> models = new ArrayList<>();

    /** What a region's connection reads in these tests: one table. */
    private static EmbeddedDatabase region;

    @BeforeAll
    static void aRegionsDatabase() {
        region = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("dashboardviewtest")
                .build();
        new JdbcTemplate(region).execute("CREATE TABLE orders AS SELECT 'SO-1' AS order_number");
    }

    @AfterAll
    static void closed() {
        region.shutdown();
    }

    private DashboardView view;

    private final UI ui = TestUi.withSession();

    DashboardViewTest() {
        modelAnswers(() -> new ScriptedProvider(
                "Shipments after the date.",
                new ToolCall(
                        "name_widget",
                        "{\"title\":\"Late shipments\",\"description\":\"Shipments after the date.\"}")));
        when(user.get()).thenReturn(new SignedInUser(8, "Lars Nygaard", Scopes.FI_EE));
        when(user.filterSignal()).thenReturn(countries.asReadonly());
        when(user.filter()).thenAnswer(call -> countries.peek());
        doAnswer(call -> {
                    countries.set(call.getArgument(0));
                    return null;
                })
                .when(user)
                .setFilter(any());
        when(connections.templateFor(any())).thenReturn(new JdbcTemplate(region));
        view = open();
    }

    /** The page as the signed-in user opens it, with what their saved dashboard holds at that moment. */
    private DashboardView open() {
        return open(Budgets.budget());
    }

    private DashboardView open(AIBudget budget) {
        var opened = new DashboardView(new PackData(), connections, providers, budget, user, dashboards, visit);
        ui.add(opened);
        return opened;
    }

    /** While the AI answers nobody, the page says so under its heading; otherwise it says nothing of it. */
    @Test
    void thePageSaysWhenTheAIAnswersNobody() {
        var paused = open(Budgets.disabled());

        assertThat(ComponentTree.descendants(paused).filter(AIPausedNotice.class::isInstance))
                .singleElement()
                .satisfies(notice -> assertThat(notice.isVisible()).isTrue());
        assertThat(ComponentTree.descendants(view).filter(AIPausedNotice.class::isInstance))
                .singleElement()
                .satisfies(notice -> assertThat(notice.isVisible()).isFalse());
    }

    /** Every widget made from now on gets a model of its own from the supplier. */
    private void modelAnswers(Supplier<ScriptedProvider> answering) {
        when(providers.forChat(anyList())).thenAnswer(call -> {
            var model = answering.get();
            models.add(model);
            return model;
        });
    }

    /** A saved table and its chat, as the dashboard keeps them. */
    private static SavedDashboards.Kept savedTable() {
        return new SavedDashboards.Kept(
                "Our orders",
                "Every order.",
                WidgetType.GRID,
                "SELECT order_number FROM orders",
                null,
                List.of(
                        new ChatMessage(ChatMessage.Role.USER, "Our orders", null, null),
                        new ChatMessage(ChatMessage.Role.ASSISTANT, "Listed by number.", null, null)));
    }

    /** A saved chart without a look, as the seeded ones are. */
    private static SavedDashboards.Kept savedChart() {
        return new SavedDashboards.Kept(
                "Orders by number",
                "One column per order.",
                WidgetType.CHART,
                "SELECT order_number AS category, 1 AS amount FROM orders",
                null,
                List.of());
    }

    @BeforeEach
    void aUi() {
        UI.setCurrent(ui);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void anEmptyDashboardIsOnePromptInTheMiddleOfThePage() {
        // the page is marked empty, which keeps its title large
        assertThat(view.hasClassName("empty")).isTrue();
        assertThat(empty().isVisible()).isTrue();
        assertThat(scroller().isVisible()).isFalse();
        assertThat(panel().getParent()).contains(empty());
        assertThat(panel().getElement().getAttribute("mode")).isEqualTo("center");
        assertThat(dashboard().getWidgets()).isEmpty();
    }

    @Test
    void aQuestionBecomesAnActiveWidgetAndThePromptMovesIntoTheNewQueryTile() {
        ask("Late shipments last month");

        assertThat(view.hasClassName("empty")).isFalse();
        assertThat(empty().isVisible()).isFalse();
        assertThat(scroller().isVisible()).isTrue();
        assertThat(dashboard().getWidgets()).hasSize(2);
        var widget = (InsightWidget) dashboard().getWidgets().getFirst();
        // the question is the title until the model names the widget, which the scripted model did at once
        assertThat(widget.getTitle()).isEqualTo("Late shipments");
        assertThat(widget.description()).isEqualTo("Shipments after the date.");
        // a chart unless the user chose a table
        assertThat(widget.getContent().getChildren()).anyMatch(Chart.class::isInstance);
        assertThat(widget.hasClassName("active")).isTrue();
        assertThat(dashboard().getWidgets().getLast().hasClassName("new-query-widget"))
                .isTrue();
        assertThat(panel().getElement().getAttribute("mode")).isEqualTo("tile");
        assertThat(descendants(dashboard().getWidgets().getLast())).contains(panel());
    }

    /**
     * The chat opens beside a new widget before the model has answered; its intro, what the widget contains in
     * business terms, follows the description the model then writes.
     */
    @Test
    void theOpenChatSaysWhatTheModelWroteTheWidgetContains() {
        ask("Late shipments last month");

        assertThat(descendants(popover())
                        .filter(c -> c.hasClassName("chat-intro"))
                        .map(c -> ((Span) c).getText()))
                .containsExactly("Shipments after the date.");
    }

    @Test
    void theNextQuestionLandsInFrontOfTheNewQueryTileAndBecomesTheActiveOne() {
        ask("Late shipments last month");
        ask("Open claims by type");

        var widgets = dashboard().getWidgets();
        assertThat(widgets).hasSize(3);
        assertThat(widgets.get(0)).isInstanceOf(InsightWidget.class);
        assertThat(widgets.get(1)).isInstanceOf(InsightWidget.class);
        assertThat(widgets.get(1).hasClassName("active")).isTrue();
        assertThat(widgets.get(0).hasClassName("active")).isFalse();
        assertThat(widgets.getLast().hasClassName("new-query-widget")).isTrue();
    }

    @Test
    void theDashboardCanBeEditedAtAllTimes() {
        assertThat(dashboard().isEditable()).isTrue();
    }

    /** The widget's chat opens beside the widget, in a popover that holds that chat and nothing of another widget's. */
    @Test
    void aQuestionShowsTheWidgetsChatInAPopoverBesideIt() {
        ask("Late shipments last month");
        ask("Open claims by type");

        var widgets = dashboard().getWidgets();
        var second = (InsightWidget) widgets.get(1);
        var first = (InsightWidget) widgets.get(0);
        assertThat(popover().getTarget()).isSameAs(second);
        assertThat(descendants(popover())).contains(second.chat()).doesNotContain(first.chat());
    }

    /** The chat stays beside its widget until the user is done with it: not modal, and a click elsewhere closes it. */
    @Test
    void theChatIsNotModalAndAClickElsewhereClosesIt() {
        assertThat(popover().isModal()).isFalse();
        assertThat(popover().isCloseOnOutsideClick()).isTrue();
    }

    /**
     * Edit on the widget whose chat is open closes the chat; the client reports the closing, and nothing is active
     * then. Edit once more opens the chat again.
     */
    @Test
    void editClosesTheOpenChatAndOpensItAgain() {
        ask("Late shipments last month");
        var widget = (InsightWidget) dashboard().getWidgets().getFirst();
        clientReportsOpened(true);

        edit(widget).click();

        assertThat(popover().isOpened()).isFalse();
        clientReportsOpened(false);
        assertThat(widget.hasClassName("active")).isFalse();

        edit(widget).click();
        clientReportsOpened(true);

        assertThat(popover().getTarget()).isSameAs(widget);
        assertThat(widget.hasClassName("active")).isTrue();
    }

    /**
     * Edit on another widget while a chat is open: the client closes the popover before the click reaches the server
     * and reports the closing after the view has opened the chat beside the other widget. That widget is the active
     * one once the client reports the opening.
     */
    @Test
    void editOnAnotherWidgetMovesTheChatThereWhateverTheClientReportsInBetween() {
        ask("Late shipments last month");
        ask("Open claims by type");
        var first = (InsightWidget) dashboard().getWidgets().get(0);
        var second = (InsightWidget) dashboard().getWidgets().get(1);
        clientReportsOpened(true);

        edit(first).click();
        clientReportsOpened(false);
        clientReportsOpened(true);

        assertThat(popover().getTarget()).isSameAs(first);
        assertThat(first.hasClassName("active")).isTrue();
        assertThat(second.hasClassName("active")).isFalse();
    }

    /** The data scope follows the person signed in: every query of a widget runs on their region's connection. */
    @Test
    void aQuestionIsAnsweredOnTheSignedInUsersRegion() {
        modelAnswers(() -> new ScriptedProvider(
                "The orders.",
                new ToolCall(
                        "update_chart_data_source",
                        "{\"queries\":[\"SELECT order_number AS category, 1 AS amount FROM orders\"]}")));

        ask("Our orders");

        verify(connections, atLeastOnce()).templateFor(Scopes.FI_EE);
        verify(connections, never()).templateFor(argThat(scope -> !scope.equals(Scopes.FI_EE)));
    }

    /**
     * The page opens with the saved dashboard: its widgets in their order, each with its chat and its query run on
     * the user's region, the New Query tile last, and nothing asked of the model.
     */
    @Test
    void theSavedDashboardOpensWithItsWidgetsAndTheirChats() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable(), savedChart()));

        view = open();

        var widgets = dashboard().getWidgets();
        assertThat(widgets).hasSize(3);
        var table = (InsightWidget) widgets.get(0);
        var chart = (InsightWidget) widgets.get(1);
        assertThat(table.getTitle()).isEqualTo("Our orders");
        assertThat(table.description()).isEqualTo("Every order.");
        assertThat(table.chat().messageList().getItems())
                .extracting(MessageListItem::getText)
                .containsExactly("Our orders", "Listed by number.");
        assertThat(table.state().sql()).isEqualTo("SELECT order_number FROM orders");
        assertThat(chart.type()).isEqualTo(WidgetType.CHART);
        assertThat(chart.state()).isNotNull();
        assertThat(widgets.getLast().hasClassName("new-query-widget")).isTrue();
        assertThat(panel().getElement().getAttribute("mode")).isEqualTo("tile");
        verify(dashboards, atLeastOnce()).forUser(8, ZoneId.of("Europe/Helsinki"));
        verify(providers).forChat(savedTable().history());
        verify(connections, never()).templateFor(argThat(scope -> !scope.equals(Scopes.FI_EE)));
        assertThat(models)
                .hasSize(2)
                .allSatisfy(model -> assertThat(model.requests()).isEmpty());
    }

    /** Save Dashboard is not offered on an empty dashboard, is offered on a saved one, and can be pressed after a change. */
    @Test
    void saveDashboardCanBePressedOnceTheDashboardDiffersFromTheSavedOne() {
        assertThat(save().isVisible()).isFalse();

        ask("Late shipments last month");

        assertThat(save().isVisible()).isTrue();
        assertThat(save().isEnabled()).isTrue();

        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();

        assertThat(save().isVisible()).isTrue();
        assertThat(save().isEnabled()).isFalse();
    }

    /**
     * Saving keeps every widget that has shown a result, in the dashboard's order, with its chat, for the user whose
     * dashboard it is; a widget without a result stays on screen, unsaved, and there is nothing left to save.
     */
    @Test
    void savingKeepsTheWidgetsThatHaveAResult() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();
        modelAnswers(() -> new ScriptedProvider(
                "One column per order.",
                new ToolCall(
                        "update_chart_data_source",
                        "{\"queries\":[\"SELECT order_number AS category, 1 AS amount FROM orders\"]}")));
        ask("Orders by number");
        modelAnswers(() -> new ScriptedProvider("The model ran no query."));
        ask("A question the model answers without a query");

        save().click();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SavedDashboards.Kept>> kept = ArgumentCaptor.forClass(List.class);
        verify(dashboards).save(eq(8), kept.capture());
        assertThat(kept.getValue())
                .extracting(SavedDashboards.Kept::title, SavedDashboards.Kept::type)
                .containsExactly(tuple("Our orders", WidgetType.GRID), tuple("Orders by number", WidgetType.CHART));
        assertThat(kept.getValue().get(1).history())
                .extracting(ChatMessage::content)
                .containsExactly("Orders by number", "One column per order.");
        assertThat(dashboard().getWidgets()).hasSize(4);
        assertThat(save().isVisible()).isTrue();
        assertThat(save().isEnabled()).isFalse();
    }

    /**
     * A save keeps the dashboard as it is, which decides nothing about what the AI proposed: the log has the turn that
     * made the widget, and no row for the save.
     */
    @Test
    void aSaveWritesNoRowToTheActivityLog() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();
        modelAnswers(() -> new ScriptedProvider(
                "One column per order.",
                new ToolCall(
                        "update_chart_data_source",
                        "{\"queries\":[\"SELECT order_number AS category, 1 AS amount FROM orders\"]}")));
        ask("Orders by number");

        save().click();

        verify(log).asked(eq(visit.as(8)), eq(ActivityView.DASHBOARD), eq("Orders by number"), anyString());
        verify(log, never()).applied(any(), any(), any());
    }

    /** A save made after a switch to another user, before the page has reloaded, goes to the dashboard on screen. */
    @Test
    void theDashboardIsSavedForTheUserWhoseDashboardItIs() {
        ask("Late shipments last month");
        when(user.get()).thenReturn(new SignedInUser(9, "Helmi Saari", Scopes.SE_NO));

        save().click();

        verify(dashboards).save(eq(8), anyList());
        verify(dashboards, never()).save(eq(9), anyList());
    }

    /** A visitor far from Helsinki reads the seeded chats in their own time zone, as the widgets date their chats. */
    @Test
    void theSeededChatsAreReadInTheVisitorsTimeZone() {
        TestUi.inTimeZone(ui, "America/Los_Angeles");

        view = open();

        verify(dashboards).forUser(8, ZoneId.of("America/Los_Angeles"));
    }

    /** A resized widget, and the removal of the last one, make the dashboard differ from the saved one. */
    @Test
    void aResizeOrARemovalCanBeSaved() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();
        var widget = (InsightWidget) dashboard().getWidgets().getFirst();

        ComponentUtil.fireEvent(
                dashboard(),
                new DashboardItemResizedEvent(
                        dashboard(), true, widget, dashboard().getChildren().toList()));

        assertThat(save().isEnabled()).isTrue();

        view = open();
        var removal = mock(DashboardItemRemoveEvent.class);
        when(removal.getItem()).thenReturn(dashboard().getWidgets().getFirst());
        dashboard().getItemRemoveHandler().onItemRemove(removal);

        assertThat(empty().isVisible()).isTrue();
        assertThat(save().isVisible()).isTrue();
        assertThat(save().isEnabled()).isTrue();
    }

    /** A widget moved to another place can be saved; the New Query tile, which the view keeps last, changes nothing. */
    @Test
    void aMovedWidgetCanBeSaved() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable(), savedChart()));
        view = open();

        ComponentUtil.fireEvent(
                dashboard(),
                new DashboardItemMovedEvent(
                        dashboard(),
                        true,
                        dashboard().getWidgets().getLast(),
                        dashboard().getChildren().toList(),
                        null));

        assertThat(save().isEnabled()).isFalse();

        ComponentUtil.fireEvent(
                dashboard(),
                new DashboardItemMovedEvent(
                        dashboard(),
                        true,
                        dashboard().getWidgets().get(1),
                        dashboard().getChildren().toList(),
                        null));

        assertThat(save().isEnabled()).isTrue();
    }

    /** A follow-up answered in a saved widget's chat can be saved: the widget, its name or its chat changed. */
    @Test
    void anAnsweredFollowUpInASavedWidgetCanBeSaved() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();
        assertThat(save().isEnabled()).isFalse();

        ((InsightWidget) dashboard().getWidgets().getFirst()).ask("Sort it the other way");

        assertThat(save().isEnabled()).isTrue();
    }

    /** A question makes the dashboard differ at once, before the model has answered it. */
    @Test
    void aNewWidgetCanBeSavedBeforeItsAnswer() {
        when(providers.forChat(anyList())).thenReturn(request -> Flux.never());

        ask("Late shipments last month");

        assertThat(save().isVisible()).isTrue();
        assertThat(save().isEnabled()).isTrue();
    }

    /** The filter is the session's: a page opens on the country another page of the visit narrowed it to. */
    @Test
    void thePageOpensOnTheSessionsCountryFilter() {
        countries.set(Scopes.FI);
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable(), savedChart()));

        view = open();

        assertThat(filter().getValue()).isEqualTo(Scopes.FI);
        verify(connections, atLeastOnce()).templateFor(Scopes.FI);
        verify(connections, never()).templateFor(argThat(scope -> !scope.equals(Scopes.FI)));
    }

    /** Choosing a country runs every widget's query again, on that country's connection, and asks the model nothing. */
    @Test
    void choosingACountryRunsEveryWidgetAgainOnItsConnection() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable(), savedChart()));
        view = open();
        clearInvocations(connections);

        filter().setValue(Scopes.FI);

        verify(user).setFilter(Scopes.FI);
        verify(connections, atLeast(2)).templateFor(Scopes.FI);
        verify(connections, never()).templateFor(argThat(scope -> !scope.equals(Scopes.FI)));
        assertThat(models).allSatisfy(model -> assertThat(model.requests()).isEmpty());
    }

    /** A saved query names no country, so choosing one leaves the dashboard as saved. */
    @Test
    void choosingACountryLeavesTheDashboardAsSaved() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();

        filter().setValue(Scopes.EE);

        assertThat(save().isEnabled()).isFalse();
    }

    @Test
    void aCountryChosenInAnotherTabIsShownHere() {
        when(dashboards.forUser(eq(8), any())).thenReturn(List.of(savedTable()));
        view = open();
        clearInvocations(connections);

        countries.set(Scopes.EE);

        assertThat(filter().getValue()).isEqualTo(Scopes.EE);
        verify(connections, atLeastOnce()).templateFor(Scopes.EE);
    }

    @Test
    void aQuestionIsAnsweredOnTheChosenCountrysConnection() {
        modelAnswers(() -> new ScriptedProvider(
                "The orders.",
                new ToolCall(
                        "update_chart_data_source",
                        "{\"queries\":[\"SELECT order_number AS category, 1 AS amount FROM orders\"]}")));
        filter().setValue(Scopes.FI);
        clearInvocations(connections);

        ask("Our orders");

        verify(connections, atLeastOnce()).templateFor(Scopes.FI);
        verify(connections, never()).templateFor(argThat(scope -> !scope.equals(Scopes.FI)));
    }

    @Test
    void theReadmeSaysWhatTheModelSeesAndPointsAtTheControllersDocumentation() {
        assertThat(view.readmeSections())
                .first()
                .extracting(section -> section.content())
                .asString()
                .contains("Only the database schema", "No data ever leaves your network");
        assertThat(view.readMore()).extracting(link -> link.text()).contains("AI-powered Grid", "AI-powered Chart");
    }

    private void ask(String question) {
        question().setValue(question);
        descendants(panel())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Ask".equals(button.getElement().getAttribute("aria-label")))
                .findFirst()
                .orElseThrow()
                .click();
    }

    private CountryFilter filter() {
        return descendants(view)
                .filter(CountryFilter.class::isInstance)
                .map(CountryFilter.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Button save() {
        return descendants(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Save Dashboard".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private TextArea question() {
        return descendants(panel())
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** What the client reports when the popover opens or closes there; a close by the server fires nothing. */
    private void clientReportsOpened(boolean opened) {
        popover().getElement().setProperty("opened", opened);
        ComponentUtil.fireEvent(popover(), new Popover.OpenedChangeEvent(popover(), true));
    }

    private static Button edit(InsightWidget widget) {
        return descendants(widget)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Edit this widget".equals(button.getElement().getAttribute("aria-label")))
                .findFirst()
                .orElseThrow();
    }

    private Popover popover() {
        return view.getChildren()
                .filter(Popover.class::isInstance)
                .map(Popover.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private QueryPanel panel() {
        return descendants(view)
                .filter(QueryPanel.class::isInstance)
                .map(QueryPanel.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Dashboard dashboard() {
        return descendants(view)
                .filter(Dashboard.class::isInstance)
                .map(Dashboard.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Component empty() {
        return view.getChildren()
                .filter(c -> c.hasClassName("dashboard-empty"))
                .findFirst()
                .orElseThrow();
    }

    private Component scroller() {
        return view.getChildren()
                .filter(c -> c.hasClassName("page-scroll"))
                .findFirst()
                .orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return root.getChildren().flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
