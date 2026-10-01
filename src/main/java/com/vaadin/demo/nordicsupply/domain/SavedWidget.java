package com.vaadin.demo.nordicsupply.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * A widget of a seeded Insights dashboard, the one every visit starts from: its title and description, its kind, the
 * query it runs, the state it is restored from (its size and, for a chart, its queries and its look; none for the
 * default), its place on the dashboard, and the chat that made it. The dataset seeds Test User's two widgets and a
 * set-up script two for each account manager. The application only reads them: what a visitor saves stays with the
 * visitor's session.
 */
@Entity
@Table(name = "saved_widgets")
@Immutable
public class SavedWidget {

    @Id
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_type")
    private WidgetType widgetType;

    @Lob
    @Column(name = "query_sql")
    private String querySql;

    @Lob
    @Column(name = "state_json")
    private String stateJson;

    @Column(name = "position")
    private Integer position;

    /** The chat, oldest first. */
    @OneToMany(mappedBy = "widget")
    @OrderBy("position")
    private List<SavedWidgetMessage> messages;

    protected SavedWidget() {}

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public WidgetType getWidgetType() {
        return widgetType;
    }

    public String getQuerySql() {
        return querySql;
    }

    public String getStateJson() {
        return stateJson;
    }

    public Integer getPosition() {
        return position;
    }

    /** The chat, oldest first. */
    public List<SavedWidgetMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SavedWidget other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
