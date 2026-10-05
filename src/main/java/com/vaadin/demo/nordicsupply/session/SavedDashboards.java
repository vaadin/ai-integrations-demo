package com.vaadin.demo.nordicsupply.session;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.data.SavedWidgetRepository;
import com.vaadin.demo.nordicsupply.domain.SavedWidget;
import com.vaadin.demo.nordicsupply.domain.WidgetType;

/**
 * The Insights dashboards of one visit: for every demo user, the seeded dashboard from the database until the visitor
 * saves their own, which then stays with the visitor's session. Everyone signs in as one of the same four employees,
 * so a dashboard saved per employee would be every visitor's: shown to the next visitor, and given to their model as
 * the memory of its chats. Kept per session, what a visitor saves is theirs alone, it lasts across reloads, tabs and
 * switches of user, and a new visit starts from the seeded dashboards again. An application with real accounts stores
 * the same records per user in its database. Not serializable, like {@link CurrentUser}.
 */
@Component
@VaadinSessionScope
public class SavedDashboards {

    /**
     * A widget as the dashboard keeps it.
     *
     * @param sql the query the widget runs; a chart keeps its queries in the state as well
     * @param stateJson the widget's size and, for a chart, its queries and its look, as the widget writes them; none
     *     for a seeded widget with the default size and look
     * @param history the widget's chat, oldest first, in the orchestrator's terms
     */
    public record Kept(
            String title,
            String description,
            WidgetType type,
            String sql,
            String stateJson,
            List<ChatMessage> history) {

        public Kept {
            history = List.copyOf(history);
        }
    }

    private final SavedWidgetRepository seeds;

    /** The dashboards this visitor saved, by the demo user's id. */
    private final Map<Integer, List<Kept>> saved = new HashMap<>();

    public SavedDashboards(SavedWidgetRepository seeds) {
        this.seeds = seeds;
    }

    /**
     * The user's dashboard in this visit: the one the visitor last saved for them, or the seeded one.
     *
     * @param zone the visitor's time zone, in which a seeded chat's wall-clock times are read, so that they show the
     *     dataset's day and the time of day they were written wherever the visitor is
     */
    public List<Kept> forUser(int userId, ZoneId zone) {
        var own = saved.get(userId);
        return own != null ? own : seeded(userId, zone);
    }

    /** Keeps the widgets, in this order, as the user's dashboard for the rest of the visit; an empty list empties it. */
    public void save(int userId, List<Kept> dashboard) {
        saved.put(userId, List.copyOf(dashboard));
    }

    private List<Kept> seeded(int userId, ZoneId zone) {
        return seeds.findByUserIdOrderByPositionAscIdAsc(userId).stream()
                .map(widget -> kept(widget, zone))
                .toList();
    }

    private static Kept kept(SavedWidget widget, ZoneId zone) {
        var history = widget.getMessages().stream()
                .map(turn -> new ChatMessage(
                        turn.getRole(),
                        turn.getContent(),
                        null,
                        turn.getSentAt().atZone(zone).toInstant()))
                .toList();
        return new Kept(
                widget.getTitle(),
                widget.getDescription(),
                widget.getWidgetType(),
                widget.getQuerySql(),
                widget.getStateJson(),
                history);
    }
}
