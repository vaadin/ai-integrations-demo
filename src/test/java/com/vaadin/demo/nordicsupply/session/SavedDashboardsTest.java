package com.vaadin.demo.nordicsupply.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.ai.chart.ChartConfigurationParser;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.spring.scopes.VaadinSessionScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.config.ScopedConnections;
import com.vaadin.demo.nordicsupply.data.SavedWidgetRepository;
import com.vaadin.demo.nordicsupply.domain.WidgetType;
import com.vaadin.demo.nordicsupply.session.SavedDashboards.Kept;

/**
 * Every visit opens each demo user's seeded dashboard, whose queries answer on the user's region and whose chats read
 * on the dataset's day in every time zone; what a visitor saves is kept for that visit, and for no other.
 */
@SpringBootTest
class SavedDashboardsTest {

    /** The demo users and their regions, as the set-up script assigns them. */
    private static final Map<Integer, Scope> USERS =
            Map.of(1, Scopes.ALL, 8, Scopes.FI_EE, 9, Scopes.SE_NO, 10, Scopes.DK_DE);

    private static final ZoneId HELSINKI = DemoClock.CLOCK.getZone();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private SavedWidgetRepository seeds;

    @Autowired
    private ScopedConnections connections;

    @Autowired
    private PackData pack;

    @Autowired
    private ConfigurableListableBeanFactory beans;

    /** One visit's dashboards; each call is a new visit. */
    private SavedDashboards visit() {
        return new SavedDashboards(seeds);
    }

    /** Without the session scope the bean is one for everyone, and one visitor's saves would be every visitor's. */
    @Test
    void eachVaadinSessionHasItsOwn() {
        assertThat(beans.getBeanDefinition("savedDashboards").getScope())
                .isEqualTo(VaadinSessionScope.VAADIN_SESSION_SCOPE_NAME);
    }

    /** Every demo user opens a dashboard of their own, two to five widgets, each with the chat that made it. */
    @Test
    void everyDemoUserOpensTheirOwnWidgetsWithTheChatsThatMadeThem() {
        var visit = visit();

        assertThat(visit.forUser(1, HELSINKI))
                .extracting(Kept::title, Kept::type)
                .containsExactly(
                        tuple("Late shipments, August 2026", WidgetType.CHART),
                        tuple("Claims open for over 7 days", WidgetType.GRID),
                        tuple("Order value by country, 2026", WidgetType.CHART),
                        tuple("Orders by channel, 2026", WidgetType.CHART));
        assertThat(USERS.keySet())
                .allSatisfy(user -> assertThat(visit.forUser(user, HELSINKI))
                        .as("user %d", user)
                        .hasSizeBetween(2, 5)
                        .allSatisfy(widget -> assertThat(widget.history())
                                .extracting(ChatMessage::role)
                                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT)));
        assertThat(USERS.keySet())
                .extracting(user -> visit.forUser(user, HELSINKI).size())
                .contains(2, 5);
    }

    /** A seeded table is two columns wide, so that its columns show; a chart keeps the width of one column. */
    @Test
    void everySeededTableIsTwoColumnsWide() {
        var visit = visit();

        USERS.keySet()
                .forEach(user -> visit.forUser(user, HELSINKI).forEach(widget -> {
                    var colspan = widget.stateJson() == null
                            ? 1
                            : JSON.readTree(widget.stateJson()).path("colspan").asInt(1);
                    assertThat(colspan).as(widget.title()).isEqualTo(widget.type() == WidgetType.GRID ? 2 : 1);
                }));
    }

    /**
     * The seeded charts show the kinds of chart a widget can be, each a look the chart controller reads: columns,
     * stacked columns, horizontal bars, lines, areas and pies.
     */
    @Test
    void theSeededChartsShowSeveralKindsOfChart() {
        var visit = visit();
        var kinds = new ArrayList<ChartType>();

        USERS.keySet()
                .forEach(user -> visit.forUser(user, HELSINKI).stream()
                        .filter(widget -> widget.type() == WidgetType.CHART)
                        .forEach(widget -> {
                            var look = widget.stateJson() == null
                                    ? null
                                    : JSON.readTree(widget.stateJson()).get("configuration");
                            kinds.add(
                                    look == null
                                            ? ChartType.COLUMN
                                            : ChartConfigurationParser.parse(look.toString())
                                                    .getChart()
                                                    .getType());
                        }));

        assertThat(kinds)
                .contains(
                        ChartType.COLUMN,
                        ChartType.BAR,
                        ChartType.LINE,
                        ChartType.AREA,
                        ChartType.AREASPLINE,
                        ChartType.PIE);
    }

    /**
     * A seeded reply is written as the model is asked to write one: a sentence or two that name no figure, since the
     * model never sees a row; a year may be named.
     */
    @Test
    void aSeededReplyNamesNoFigure() {
        var visit = visit();

        USERS.keySet()
                .forEach(user -> visit.forUser(user, HELSINKI).forEach(widget -> {
                    var reply = widget.history().getLast().content();
                    assertThat(reply.replaceAll("\\b20\\d\\d\\b", ""))
                            .as(widget.title())
                            .doesNotContainPattern("\\d");
                }));
    }

    /** Every seeded query runs on its owner's region, through the guard the model's queries pass, and has rows. */
    @Test
    void everySeededQueryAnswersOnItsOwnersRegion() {
        var visit = visit();

        USERS.forEach((user, scope) -> {
            var db = new JdbcDatabaseProvider(() -> connections.templateFor(scope), pack);
            visit.forUser(user, HELSINKI)
                    .forEach(widget -> assertThat(db.executeQuery(widget.sql()))
                            .as("%s of user %d", widget.title(), user)
                            .isNotEmpty());
        });
    }

    /** Counted from the dataset's day, not from the database server's clock, a widget shows the same rows every day. */
    @Test
    void noSeededQueryReadsTheServersClock() {
        var visit = visit();

        USERS.keySet()
                .forEach(user -> assertThat(visit.forUser(user, HELSINKI))
                        .extracting(Kept::sql)
                        .noneMatch(sql -> sql.contains("CURRENT_")));
    }

    /**
     * A seeded chat's times are wall-clock times on the dataset's day, so a visitor reads the time of day the turn was
     * written, on 7 September, wherever the visitor is.
     */
    @Test
    void aSeededChatReadsOnTheDatasetsDayInEveryTimeZone() {
        for (var zone : List.of(HELSINKI, ZoneId.of("America/Los_Angeles"), ZoneId.of("Asia/Tokyo"))) {
            assertThat(visit().forUser(1, zone).getFirst().history())
                    .as(zone.getId())
                    .extracting(turn -> turn.time().atZone(zone).toLocalDateTime())
                    .containsExactly(LocalDateTime.of(2026, 9, 7, 9, 11, 20), LocalDateTime.of(2026, 9, 7, 9, 12));
        }
    }

    /**
     * A save keeps the dashboard for the rest of the visit, in its order and with its chats. Another visit still opens
     * the seeded one, and so does another user in the same visit.
     */
    @Test
    void aSaveIsKeptForTheVisitAndForNoOther() {
        var visit = visit();
        var seeded = visit.forUser(10, HELSINKI);
        var asked = LocalDateTime.of(2026, 9, 7, 14, 3).atZone(HELSINKI).toInstant();
        var added = new Kept(
                "Late deliveries by carrier",
                "Per carrier.",
                WidgetType.CHART,
                "SELECT carrier FROM shipments",
                "{\"colspan\":2,\"rowspan\":1}",
                List.of(new ChatMessage(ChatMessage.Role.USER, "Late deliveries by carrier", null, asked)));

        visit.save(10, List.of(added, seeded.getLast()));

        assertThat(visit.forUser(10, HELSINKI)).containsExactly(added, seeded.getLast());
        assertThat(visit().forUser(10, HELSINKI)).isEqualTo(seeded);
        assertThat(visit.forUser(9, HELSINKI)).isEqualTo(visit().forUser(9, HELSINKI));
    }

    /** An emptied dashboard stays empty for the visit, rather than going back to the seeded one. */
    @Test
    void anEmptiedDashboardStaysEmpty() {
        var visit = visit();

        visit.save(10, List.of());

        assertThat(visit.forUser(10, HELSINKI)).isEmpty();
    }

    /** What was saved does not change with the list it came from, nor with a widget's chat list. */
    @Test
    void whatWasSavedDoesNotChangeAfterwards() {
        var visit = visit();
        var chat = new ArrayList<ChatMessage>(
                visit.forUser(10, HELSINKI).getFirst().history());
        var widget = new Kept("Orders", "Orders.", WidgetType.GRID, "SELECT 1", null, chat);
        var dashboard = new ArrayList<>(List.of(widget));

        visit.save(10, dashboard);
        dashboard.clear();
        chat.clear();

        assertThat(visit.forUser(10, HELSINKI))
                .singleElement()
                .satisfies(saved -> assertThat(saved.history()).hasSize(2));
    }
}
