package com.vaadin.demo.nordicsupply.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dashboard.Dashboard;
import com.vaadin.flow.component.dashboard.DashboardWidget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;

import com.vaadin.demo.nordicsupply.ai.Conversations;
import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;
import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.config.ScopedConnections;
import com.vaadin.demo.nordicsupply.domain.ActivityView;
import com.vaadin.demo.nordicsupply.domain.WidgetType;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.SavedDashboards;
import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.components.AIPausedNotice;
import com.vaadin.demo.nordicsupply.ui.components.CountryFilter;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;
import com.vaadin.demo.nordicsupply.ui.components.InsightWidget;
import com.vaadin.demo.nordicsupply.ui.components.QueryPanel;

/**
 * The self-service dashboard. The page opens with the signed-in user's dashboard of this visit, the
 * seeded one until the visitor saves their own: its widgets with their chats, their queries run anew. An empty
 * dashboard shows one prompt in the middle of the page. A question adds a grid or chart widget and opens its chat in
 * a popover beside it; the Edit button in a widget's header opens it again, and closes it while it is open. The last
 * tile of the dashboard is "New Query" and holds the same prompt, so the next question is typed where the next
 * widget will appear. The popover also closes with Escape, its close button, or a click anywhere else on the page;
 * its own widget is not elsewhere, so the table or chart can be used while the chat is open. Widgets can be moved,
 * resized and removed; Save Dashboard, in the page's heading, keeps the dashboard as it is on screen for the rest of
 * the visit, and until then a reload goes back to the saved one.
 * <p>
 * The country filter in the page's heading narrows the dashboard to one of the user's countries, or widens it back to
 * their region. It is the session's, so every tab follows it, and a change runs every widget's query again on that
 * scope's connection; the model is not asked, and the dashboard stays as saved, since a saved query names no country.
 * <p>
 * Every widget's turns go to the activity log.
 * <p>
 * The dashboard's own selection (the state its move, resize and remove controls need) follows keyboard focus and is
 * cleared as soon as focus leaves the widget, which it does when the user types into the chat. The view therefore
 * keeps its own notion of the <em>active</em> widget, the one whose chat the popover shows, and never reads the
 * dashboard's selection.
 */
@Route("dashboard")
@PageTitle("Dashboard")
@Menu(order = 1, title = "Dashboard", icon = "icons/file-chart-column.svg")
public class DashboardView extends PageView implements HasReadme {

    /** Three tiles across, as designed; the popover beside a widget is about one tile wide. */
    private static final int MAX_COLUMNS = 3;

    /** Narrower than this and a chart's axis labels collide; three columns fit from about 1100px. */
    private static final String MIN_COLUMN_WIDTH = "260px";

    /** The widgets' proportions as designed: about 4:3 at three columns on a 1440px screen. */
    private static final String ROW_HEIGHT = "300px";

    private static final String GAP = "1rem";

    /** The popover beside a widget: as wide as a one-column widget, and tall enough for a few turns of chat. */
    private static final String POPOVER_WIDTH = "400px";

    private static final String POPOVER_HEIGHT = "500px";

    private static final List<ReadmeSection> README_SECTIONS = List.of(
            new ReadmeSection("Create and Edit Widgets with AI", """
                    **Ask questions about orders, shipments, claims or products**. The answer appears as a chart or table on your dashboard.

                    Only the database schema is sent to the AI model. No data ever leaves your network. Every widget shows what data it's based on.

                    The **Activity Log**, at the foot of the menu, records each question, what the model could see and the query it wrote.

                    Click **Edit** to refine a widget, for example: "group it by week", "only the Göteborg warehouse", "add the customer", "call it Late orders".
                    """),
            new ReadmeSection("Filter by Country", """
                    **The country filter** at the top of the page narrows the dashboard to one of your countries.
                    The database applies it, so it can only narrow what your region shows.
                    """),
            new ReadmeSection("Save Your Dashboard", """
                    Move, resize and remove widgets
                    as you like, and press **Save Dashboard** to keep the dashboard as it is, chats included, for the rest of
                    your visit.
                    """));

    private static final List<Link> READ_MORE = List.of(
            new Link(
                    "AI-powered Grid",
                    "https://vaadin.com/docs/latest/flow/ai-support/ai-powered-grid",
                    "The controller behind a table widget"),
            new Link(
                    "AI-powered Chart",
                    "https://vaadin.com/docs/latest/flow/ai-support/ai-powered-chart",
                    "The controller behind a chart widget"),
            new Link(
                    "AI support in Vaadin Flow",
                    "https://vaadin.com/docs/latest/flow/ai-support",
                    "Overview of the AI support"));

    /** Suggested questions: the dataset's for a table, and three that fit a chart's shape. */
    private static final List<String> TABLE_CHIPS =
            List.of("Late shipments last month, by week", "Claims open over 7 days", "Backorder lines by category");

    private static final List<String> CHART_CHIPS =
            List.of("Late shipments last month, by week", "Orders per month this year", "Open claims by type");

    private final PackData pack;
    private final ScopedConnections connections;
    private final Conversations conversations;
    private final AIBudget budget;
    private final CurrentUser user;
    private final SavedDashboards dashboards;
    private final Visit visit;

    /**
     * Whose dashboard this is: the user signed in when the page was built. A switch reloads every tab, and a save
     * made before the reload lands still goes to the dashboard on screen.
     */
    private final int ownerId;

    private final Dashboard dashboard = new Dashboard();
    private final QueryPanel query;
    private final Div empty = new Div();

    /** What the active widget contains, in business terms, over its chat; the widget itself shows only its title. */
    private final Span intro = new Span();

    private final Div scroller = new Div(dashboard);
    private final DashboardWidget newQuery = new DashboardWidget();

    /** The New Query tile's content: a title and, while the dashboard has widgets, the prompt. */
    private final Div newQueryBox = new Div();

    private final Popover popover = new Popover();
    private InsightWidget active;

    private final Button save = new Button("Save Dashboard", e -> save());

    private final CountryFilter filter;

    /** The country filter the widgets on screen last ran their queries under. */
    private Scope shown;

    /** Whether the dashboard on screen differs from the saved one. */
    private boolean dirty;

    public DashboardView(
            PackData pack,
            ScopedConnections connections,
            Conversations conversations,
            AIBudget budget,
            CurrentUser user,
            SavedDashboards dashboards,
            Visit visit) {
        super("dashboard", "Dashboard", "Understand Your Operations", "Dashboard");
        this.pack = pack;
        this.connections = connections;
        this.conversations = conversations;
        this.budget = budget;
        this.user = user;
        this.dashboards = dashboards;
        this.visit = visit;
        this.ownerId = user.get().id();
        filter = new CountryFilter(user.get().scope());
        // the filter shows the session's choice, as every tab does, and a choice made here becomes the session's
        filter.bindValue(user.filterSignal(), user::setFilter);
        shown = user.filter();
        heading().addActions(filter, save);

        query = new QueryPanel(
                Map.of(WidgetType.GRID, TABLE_CHIPS, WidgetType.CHART, CHART_CHIPS),
                budget.limits().maxQuestionLength(),
                this::ask);

        dashboard.setWidthFull();
        dashboard.setEditable(true);
        dashboard.setMaximumColumnCount(MAX_COLUMNS);
        dashboard.setMinimumColumnWidth(MIN_COLUMN_WIDTH);
        dashboard.setRowHeight(ROW_HEIGHT);
        dashboard.setGap(GAP);
        dashboard.setItemRemoveHandler(e -> {
            if (e.getItem() instanceof InsightWidget widget) {
                remove(widget);
            }
        });
        dashboard.addItemMovedListener(e -> {
            if (e.getItem() instanceof InsightWidget) {
                markDirty();
            }
            keepNewQueryLast();
        });
        dashboard.addItemResizedListener(e -> {
            if (e.getItem() instanceof InsightWidget) {
                markDirty();
            }
        });

        var tileTitle = new Span("New Query");
        tileTitle.addClassName("tile-title");
        newQueryBox.add(tileTitle);
        newQueryBox.addClassName("new-query-box");
        newQuery.setContent(newQueryBox);
        newQuery.addClassName("new-query-widget");
        // The dashboard is editable at all times and has no per-widget switch, so the tile opts out itself. Its
        // header, with the move and remove buttons, is hidden by CSS. The drag is refused here, before the dashboard
        // sees it. A move by keyboard is undone by keepNewQueryLast and a keyboard remove is ignored by the remove
        // handler, so neither needs blocking.
        newQuery.getElement()
                .addEventListener("dragstart", e -> {})
                .preventDefault()
                .stopPropagation();
        // An editable widget puts an invisible cover over its content that selects the widget on click. It lives in
        // the widget's shadow root without a part, so no Flow or CSS API reaches it; this is the one place the view
        // runs a script. With clicks passing through, a click on the prompt focuses the prompt. The tile leaves the
        // dashboard whenever the last widget goes, and Flow creates a new element for it when it comes back, so the
        // script runs on every attach.
        newQuery.addAttachListener(event -> newQuery.getElement().executeJs("""
                        this.updateComplete.then(() => {
                            this.shadowRoot.getElementById('focus-button-wrapper').style.pointerEvents = 'none';
                        });
                        """));

        popover.setOpenOnClick(false);
        // not modal, and closed by a click elsewhere: the popover counts a click on its target, the widget, as
        // inside, so the table or chart beside the chat stays usable while the chat is open
        popover.setModal(false);
        popover.setCloseOnOutsideClick(true);
        popover.setPosition(PopoverPosition.END_TOP);
        popover.setWidth(POPOVER_WIDTH);
        popover.setHeight(POPOVER_HEIGHT);
        popover.setAutofocus(true);
        popover.addClassName("dashboard-widget-popover");
        // The client reports every opening and closing, whether the user, the browser or the view caused it, and
        // a report can arrive after the view has moved on: Edit on another widget closes the popover on the client
        // before the click reaches the server, the view then opens it beside that widget, and the report of the
        // closing comes in between. So the active widget follows the reports: the popover's target while it is
        // open, none while it is closed.
        popover.addOpenedChangeListener(
                e -> setActive(e.isOpened() && popover.getTarget() instanceof InsightWidget widget ? widget : null));
        empty.addClassName("dashboard-empty");
        intro.addClassName("chat-intro");
        scroller.addClassName("page-scroll");

        add(new AIPausedNotice(budget), empty, scroller, popover);
        loadSaved();
        layout();
        // a change of the filter, made here or in another tab, runs every widget's query again on the new scope's
        // connection. A switch of user resets the filter as well, but every tab then reloads as the new user.
        Signal.effect(this, () -> {
            var chosen = user.filterSignal().get();
            if (user.get().id() == ownerId && !chosen.equals(shown)) {
                shown = chosen;
                widgets().forEach(InsightWidget::rerun);
            }
        });
    }

    @Override
    public String readme() {
        return null;
    }

    @Override
    public List<ReadmeSection> readmeSections() {
        return README_SECTIONS;
    }

    @Override
    public List<Link> readMore() {
        return READ_MORE;
    }

    /**
     * The user's dashboard of this visit, each widget with its chat and its query run anew; nothing is sent to the
     * model. A seeded chat's times are read in the visitor's time zone, as the widgets date their chats.
     */
    private void loadSaved() {
        var zone = DemoClock.forVisitor(UI.getCurrent()).getZone();
        for (var saved : dashboards.forUser(ownerId, zone)) {
            var widget = newWidget(saved.type(), saved.title(), saved.history());
            widget.setDescription(saved.description());
            dashboard.add(widget);
            widget.restore(saved.sql(), saved.stateJson());
        }
    }

    /**
     * Empty: the prompt in the middle, nothing else. With widgets: the dashboard, ending with the New Query tile that
     * holds the prompt. Save Dashboard shows while there is something to save, a widget or the removal of the last
     * one, and can be pressed while the dashboard differs from the saved one. An empty page is marked {@code empty},
     * which keeps its title large, as the design draws the empty dashboard.
     */
    private void layout() {
        boolean hasWidgets = !widgets().isEmpty();
        getElement().getClassList().set("empty", !hasWidgets);
        empty.setVisible(!hasWidgets);
        scroller.setVisible(hasWidgets);
        save.setVisible(hasWidgets || dirty);
        save.setEnabled(dirty);
        if (!hasWidgets) {
            popover.close();
            dashboard.remove(newQuery);
            query.setMode(QueryPanel.Mode.CENTER);
            empty.removeAll();
            empty.add(query);
        } else {
            if (!dashboard.getWidgets().contains(newQuery)) {
                dashboard.add(newQuery);
            }
            if (query.getParent().orElse(null) != newQueryBox) {
                query.setMode(QueryPanel.Mode.TILE);
                newQueryBox.add(query); // moves it out of the empty state
            }
        }
    }

    /** The New Query tile is always the last one; a move that put it elsewhere is undone. */
    private void keepNewQueryLast() {
        var widgets = dashboard.getWidgets();
        if (widgets.contains(newQuery) && widgets.getLast() != newQuery) {
            dashboard.remove(newQuery);
            dashboard.add(newQuery);
        }
    }

    /** Opens the popover with the widget's chat beside it, or closes it when it is open beside this widget. */
    private void activate(InsightWidget widget) {
        if (widget == active && popover.isOpened()) {
            popover.close(); // Edit again: the client reports the closing, and nothing is active then
            return;
        }
        setActive(widget);
        var footnote = new Span(QueryPanel.FOOTNOTE);
        footnote.addClassName("footnote");
        var content = new Div();
        intro.setText(widget.description());
        content.add(intro, widget.chat(), footnote);
        content.addClassName("popover-chat");
        var close = new Button(new SvgIcon("icons/x.svg"), e -> popover.close());
        close.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        close.addClassName("popover-close");
        close.setAriaLabel("Close the chat");
        close.setTooltipText("Close the chat");
        popover.removeAll();
        popover.add(close, content);
        if (popover.getTarget() != widget) {
            popover.setTarget(widget);
        }
        // a widget below the fold would leave the popover beside it off-screen too
        widget.scrollIntoView(ScrollIntoViewOption.Block.NEAREST);
        openAfterRender();
    }

    /**
     * Opens the popover once the browser has laid the dashboard out. Adding or moving a tile makes the dashboard
     * re-render its cells in the same round trip; a popover opened in that round trip is closed again by the client
     * because its target moved, and the closing would also clear the active widget. Waiting two frames avoids that.
     */
    private void openAfterRender() {
        // opened on the client, where the frames are; the property syncs back to the server
        popover.getElement()
                .executeJs("requestAnimationFrame(() => requestAnimationFrame(() => { this.opened = true; }))");
    }

    /** Marks the active widget with the gold outline and clears the previous one. */
    private void setActive(InsightWidget widget) {
        if (active != null && active != widget) {
            active.removeClassName("active");
        }
        active = widget;
        if (widget != null) {
            widget.addClassName("active");
        }
    }

    /** A question adds one widget of the chosen kind in front of the New Query tile and asks it there. */
    private void ask(String question, WidgetType type) {
        var widget = newWidget(type, question, List.of());
        int index = dashboard.getWidgets().indexOf(newQuery);
        if (index < 0) {
            dashboard.add(widget);
        } else {
            dashboard.addWidgetAtIndex(index, widget);
        }
        markDirty();
        layout();
        activate(widget);
        widget.ask(question);
    }

    /**
     * A widget on the connection of the country filter, read for every query: the signed-in user's region, or the
     * country it is narrowed to. Every answered turn makes the dashboard differ from the saved one.
     */
    private InsightWidget newWidget(WidgetType type, String title, List<ChatMessage> history) {
        var db = new JdbcDatabaseProvider(() -> connections.templateFor(user.filter()), pack);
        var turns = visit.turns(ownerId, ActivityView.DASHBOARD);
        var widget = new InsightWidget(
                type, title, db, conversations, budget, user.get().name(), history, turns);
        widget.onAsk(this::activate);
        widget.onChanged(changed -> {
            markDirty();
            // the model may have described the widget anew; the chat beside it says so at once
            if (changed == active) {
                intro.setText(changed.description());
            }
        });
        return widget;
    }

    private void remove(InsightWidget widget) {
        if (widget == active) {
            popover.close();
        }
        dashboard.remove(widget);
        markDirty();
        layout();
    }

    private List<InsightWidget> widgets() {
        return dashboard.getWidgets().stream()
                .filter(InsightWidget.class::isInstance)
                .map(InsightWidget.class::cast)
                .toList();
    }

    private void markDirty() {
        dirty = true;
        save.setVisible(true);
        save.setEnabled(true);
    }

    /**
     * Keeps the dashboard on screen for the rest of the visit: every widget that has shown a result, in the
     * dashboard's order, with its chat. A widget without a result is left out, and the notification says so.
     */
    private void save() {
        var widgets = widgets();
        var kept = new ArrayList<SavedDashboards.Kept>();
        for (var widget : widgets) {
            var state = widget.state();
            if (state != null) {
                kept.add(new SavedDashboards.Kept(
                        widget.getTitle(),
                        widget.description(),
                        widget.type(),
                        state.sql(),
                        state.json(),
                        widget.history()));
            }
        }
        dashboards.save(ownerId, kept);
        dirty = false;
        layout();
        int left = widgets.size() - kept.size();
        var saved = Notification.show(
                switch (left) {
                    case 0 -> "Dashboard saved";
                    case 1 -> "Dashboard saved; a widget without a result was not kept";
                    default -> "Dashboard saved; " + left + " widgets without a result were not kept";
                });
        saved.addThemeVariants(NotificationVariant.SUCCESS);
    }
}
