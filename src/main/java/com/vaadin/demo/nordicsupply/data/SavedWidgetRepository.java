package com.vaadin.demo.nordicsupply.data;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.SavedWidget;

/** The seeded Insights dashboards, each widget with its chat. */
public interface SavedWidgetRepository extends JpaRepository<SavedWidget, Integer> {

    /** One user's seeded widgets in the dashboard's order, each with its chat. */
    @EntityGraph(attributePaths = "messages")
    List<SavedWidget> findByUserIdOrderByPositionAscIdAsc(Integer userId);
}
