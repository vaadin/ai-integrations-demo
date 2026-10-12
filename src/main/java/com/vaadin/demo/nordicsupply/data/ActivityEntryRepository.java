package com.vaadin.demo.nordicsupply.data;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.ActivityEntry;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/** The activity log: written by {@link ActivityLog}, read one visit at a time by the Activity log view. */
public interface ActivityEntryRepository extends JpaRepository<ActivityEntry, Long> {

    /** A row of the Activity log view: the entry with the employee's name joined in. */
    record Row(
            LocalDateTime occurredAt,
            String who,
            ActivityView view,
            String prompt,
            String modelCouldSee,
            String proposal,
            ActivityEntry.Decision decision,
            String rule,
            String model,
            String finishReason,
            Integer inputTokens,
            Integer outputTokens) {}

    /**
     * The rows one visit wrote, of the employee with {@code userId} or, when it is null, of every employee the visit
     * signed in as, matching a {@link SearchTerms#like} term on the prompt or the proposal; every such row for
     * {@code ""}. The two are text columns of any length, which {@code lower} takes only as strings.
     */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.ActivityEntryRepository$Row(
                a.occurredAt, s.fullName, a.view, a.prompt, a.dataScope, a.proposal, a.decision, a.rejectionRule,
                a.modelName, a.finishReason, a.inputTokens, a.outputTokens)
            from ActivityEntry a left join Staff s on s.id = a.userId
            where a.visit = :visit and (:userId is null or a.userId = :userId)
              and (:term = '' or lower(cast(a.prompt as String)) like :term escape '\\'
                   or lower(cast(a.proposal as String)) like :term escape '\\')
            """)
    Page<Row> search(
            @Param("term") String term, @Param("visit") UUID visit, @Param("userId") Integer userId, Pageable pageable);

    /** Deletes the rows one visit wrote, and forgets any of them already loaded. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ActivityEntry a where a.visit = :visit")
    void deleteByVisit(@Param("visit") UUID visit);
}
