package com.vaadin.demo.nordicsupply.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.vaadin.flow.component.ai.common.ChatMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * One turn of a seeded widget's chat: who said it, what, and when, in the orchestrator's own terms. Read only, like
 * its widget.
 */
@Entity
@Table(name = "saved_widget_messages")
@Immutable
public class SavedWidgetMessage {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "widget_id")
    private SavedWidget widget;

    @Column(name = "position")
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private ChatMessage.Role role;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected SavedWidgetMessage() {}

    public Long getId() {
        return id;
    }

    public SavedWidget getWidget() {
        return widget;
    }

    public int getPosition() {
        return position;
    }

    public ChatMessage.Role getRole() {
        return role;
    }

    /** The text as it was said; a reply keeps the model's title and summary lines. */
    public String getContent() {
        return content;
    }

    /** When the turn was said, as a wall-clock time on the dataset's day, which reads the same in every time zone. */
    public LocalDateTime getSentAt() {
        return sentAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SavedWidgetMessage other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
