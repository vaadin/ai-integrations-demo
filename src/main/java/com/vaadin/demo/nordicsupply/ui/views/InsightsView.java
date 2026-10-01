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
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.vaadin.demo.nordicsupply.ai.Conversations;
import com.vaadin.demo.nordicsupply.ai.JdbcDatabaseProvider;
import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.config.PackData;
import com.vaadin.demo.nordicsupply.config.ScopedConnections;
import com.vaadin.demo.nordicsupply.domain.WidgetType;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.SavedDashboards;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;
import com.vaadin.demo.nordicsupply.ui.components.InsightWidget;
import com.vaadin.demo.nordicsupply.ui.components.QueryPanel;

/**
 * The self-service dashboard, "Insights". The page opens with the signed-in user's dashboard of this visit, the
 * seeded one until the visitor saves their own: its widgets with their chats, their queries run anew. An empty
 * dashboard shows one prompt in the middle of the page. A question adds a grid or chart widget and opens its chat in
 * a popover beside it; the Edit button in a widget's header opens it again. The last tile of the dashboard is "New
 * Query" and holds the same prompt, so the next question is typed where the next widget will appear. The popover
 * closes with Escape or its close button. Widgets can be moved, resized and removed; Save Dashboard, in the page's
 * heading, keeps the dashboard as it is on screen for the rest of the visit, and until then a reload goes back to
 * the saved one.
 * <p>
 * The dashboard's own selection (the state its move, resize and remove controls need) follows keyboard focus and is
 * cleared as soon as focus leaves the widget, which it does when the user types into the chat. The view therefore
 * keeps its own notion of the <em>active</em> widget, set when its chat opens, and never reads the dashboard's
 * selection.
 */
@Route("insights")
@PageTitle("Insights")
@Menu(order = 1, title = "Insights", icon = "icons/file-chart-column.svg")
public class InsightsView extends PageView implements HasReadme {

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

    private static final String README = """
            ### What this page does

            You are an analyst on the **Nordic Supply** order desk. Type a question about orders, shipments, claims or
            products, choose whether to render it as a chart or a table, and press the arrow. The answer appears as a
            widget on your dashboard, named and described by the assistant in business terms. **Edit** in a widget's
            header opens its chat beside it, so you can refine it: "group it by week", "only the Göteborg warehouse",
            "add the customer", "call it Late orders". A table stays a table and a chart stays a chart; the **New
            Query** tile at the end of the dashboard asks the next question.

            ### Things to try

            * *Which orders shipped after the promised date last month, by customer*
            * *Late shipments last month, by week* as a chart, then in its chat: *make it a line chart*
            * *Which carrier delivered late most often last month?*
            * *Claims open over 7 days*
            * Open a saved widget's chat: the question that made it is still there, and a follow-up is answered in
              context.

            ### How you know the answer is right

            Every widget says what it contains and what was counted. Ask its chat which data the answer is based on and
            how it was worked out. When a question can be read in two ways, the assistant tells you which reading it
            used.

            ### What the model sees

            Only the database schema and today's date. The rows you see never leave your network: the model writes a
            query, the application runs it on a read-only account, and the result goes straight to the widget. A
            question sees the rows of your own region only.

            ### Your dashboard

            Every demo user starts with a dashboard of their own, two to five widgets. Move, resize and remove widgets
            as you like, and press **Save Dashboard** to keep the dashboard as it is, chats included, for the rest of
            your visit: a reload, another tab or a switch of user and back brings it back. Until then, leaving the
            page or reloading it goes back to the saved one. What you save is yours alone: nobody else sees it, and a
            new visit starts from the seeded widgets again. A widget without a result is not kept. To rename a widget,
            tell its chat the name you want.
            """;

    private static final List<Link> READ_MORE = List.of(
            new Link(
                    "AI-powered Grid",
                    "https://vaadin.com/docs/latest/flow/ai-support/ai-powered-grid",
                    "the controller behind a table widget"),
            new Link(
                    "AI-powered Chart",
                    "https://vaadin.com/docs/latest/flow/ai-support/ai-powered-chart",
                    "the controller behind a chart widget"),
            new Link("AI support in Vaadin Flow", "https://vaadin.com/docs/latest/flow/ai-support", "the overview"));

    /** Suggested questions: the dataset's for a table, and three that fit a chart's shape. */
    private static final List<String> TABLE_CHIPS =
            List.of("Late shipments last month, by week", "Claims open over 7 days", "Backorder lines by category");

    private static final List<String> CHART_CHIPS =
            List.of("Late shipments last month, by week", "Orders per month this year", "Open claims by type");

    private final PackData pack;
    private final ScopedConnections connections;
    private final Conversations conversations;
    private final CurrentUser user;
    private final SavedDashboards dashboards;

    /**
     * Whose dashboard this is: the user signed in when the page was built. A switch reloads every tab, and a save
     * made before the reload lands still goes to the dashboard on screen.
     */
    private final int ownerId;

    private final Dashboard dashboard = new Dashboard();
    private final QueryPanel query;
    private final Div empty = new Div();
    private final Div scroller = new Div(dashboard);
    private final DashboardWidget newQuery = new DashboardWidget();

    /** The New Query tile's content: a title and, while the dashboard has widgets, the prompt. */
    private final Div newQueryBox = new Div();

    private final Popover popover = new Popover();
    private InsightWidget active;

    private final Button save = new Button("Save Dashboard", e -> save());

    /** Whether the dashboard on screen differs from the saved one. */
    private boolean dirty;

    public InsightsView(
            PackData pack,
            ScopedConnections connections,
            Conversations conversations,
            CurrentUser user,
            SavedDashboards dashboards) {
        super("insights", "Insights", "Understand Your Operations", "Insights");
        this.pack = pack;
        this.connections = connections;
        this.conversations = conversations;
        this.user = user;
        this.dashboards = dashboards;
        this.ownerId = user.get().id();
        heading().addActions(save);

        query = new QueryPanel(Map.of(WidgetType.GRID, TABLE_CHIPS, WidgetType.CHART, CHART_CHIPS), this::ask);

        dashboard.addClassName("insights-dashboard");
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
        popover.setModal(false);
        popover.setCloseOnOutsideClick(false);
        popover.setPosition(PopoverPosition.END_TOP);
        popover.setWidth(POPOVER_WIDTH);
        popover.setHeight(POPOVER_HEIGHT);
        popover.setAutofocus(true);
        popover.addClassName("insight-popover");
        popover.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                setActive(null); // Escape on the client, or a close from the view: either way nothing is active
            }
        });
        empty.addClassName("insights-empty");
        scroller.addClassName("page-scroll");

        add(empty, scroller, popover);
        loadSaved();
        layout();
    }

    @Override
    public String readme() {
        return README;
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
     * one, and can be pressed while the dashboard differs from the saved one.
     */
    private void layout() {
        boolean hasWidgets = !widgets().isEmpty();
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

    /** Opens the popover with the widget's chat beside it. */
    private void activate(InsightWidget widget) {
        if (widget == active && popover.isOpened()) {
            return;
        }
        setActive(widget);
        var footnote = new Span(QueryPanel.FOOTNOTE);
        footnote.addClassName("footnote");
        var content = new Div();
        // the widget itself shows only its title; what it contains, in business terms, is said here
        var intro = new Span(widget.description());
        intro.addClassName("chat-intro");
        content.add(intro, widget.chat(), widget.thinkingIndicator(), footnote);
        content.addClassName("popover-chat");
        var close = new Button(VaadinIcon.CLOSE.create(), e -> popover.close());
        close.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        close.addClassName("popover-close");
        close.setAriaLabel("Close");
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

    /** A widget on the signed-in user's connection; every answered turn makes the dashboard differ from the saved one. */
    private InsightWidget newWidget(WidgetType type, String title, List<ChatMessage> history) {
        var db = new JdbcDatabaseProvider(
                () -> connections.templateFor(user.get().scope()), pack);
        var widget =
                new InsightWidget(type, title, db, conversations, user.get().name(), history);
        widget.onAsk(this::activate);
        widget.onChanged(changed -> markDirty());
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
        Notification.show(
                switch (left) {
                    case 0 -> "Dashboard saved";
                    case 1 -> "Dashboard saved; a widget without a result was not kept";
                    default -> "Dashboard saved; " + left + " widgets without a result were not kept";
                });
    }
}
