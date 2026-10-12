package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.vaadin.demo.nordicsupply.domain.ActivityEntry.Decision;
import com.vaadin.demo.nordicsupply.domain.ActivityView;

/**
 * The log records a turn as it goes out and completes it as it ends, keeps what the model proposed over its answer,
 * records a kept result and a refused prompt as decisions, and forgets a visit's rows when the visit ends.
 */
@SpringBootTest
@Transactional
class ActivityLogTest {

    @Autowired
    private ActivityLog log;

    @Autowired
    private ActivityEntryRepository entries;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${spring.ai.openai.chat.model}")
    private String model;

    private final ActivityLog.Author lars = new ActivityLog.Author(UUID.randomUUID(), 8);

    @Test
    void aTurnIsRecordedOnTheDatasetsDayWithTheModelItGoesTo() {
        log.asked(lars, ActivityView.DASHBOARD, "Open claims", "The schema");

        assertThat(rows(lars.visit())).singleElement().satisfies(row -> {
            assertThat(row.occurredAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 7));
            assertThat(row.who()).isEqualTo("Lars Nygaard");
            assertThat(row.view()).isEqualTo(ActivityView.DASHBOARD);
            assertThat(row.prompt()).isEqualTo("Open claims");
            assertThat(row.modelCouldSee()).isEqualTo("The schema");
            assertThat(row.model()).isEqualTo(model);
            assertThat(row.proposal()).isNull();
            assertThat(row.decision()).isNull();
        });
    }

    @Test
    void anEndedTurnHasItsAnswerWhyTheModelStoppedAndItsTokens() {
        var turn = log.asked(lars, ActivityView.DASHBOARD, "Open claims", "The schema");

        log.ended(turn, "A table of open claims.", "STOP", 900, 40);

        assertThat(rows(lars.visit()))
                .extracting(
                        ActivityEntryRepository.Row::proposal,
                        ActivityEntryRepository.Row::inputTokens,
                        ActivityEntryRepository.Row::outputTokens)
                .containsExactly(tuple("A table of open claims.", 900, 40));
        entityManager.flush();
        assertThat(jdbc.queryForObject("select finish_reason from activity_log where id = ?", String.class, turn))
                .isEqualTo("STOP");
    }

    /** The controller reports a proposal just after the turn's end; either order leaves the proposal. */
    @Test
    void whatTheModelProposedTakesThePlaceOfItsAnswer() {
        var answeredFirst = log.asked(lars, ActivityView.DASHBOARD, "Open claims", "The schema");
        log.ended(answeredFirst, "A table of open claims.", "STOP", 900, 40);
        log.proposed(answeredFirst, "SELECT claim_number FROM claims");
        var proposedFirst = log.asked(lars, ActivityView.DASHBOARD, "Late shipments", "The schema");
        log.proposed(proposedFirst, "SELECT shipment_number FROM late_shipments");
        log.ended(proposedFirst, "A table of late shipments.", "STOP", 900, 40);

        assertThat(rows(lars.visit()))
                .extracting(ActivityEntryRepository.Row::proposal)
                .containsExactlyInAnyOrder(
                        "SELECT claim_number FROM claims", "SELECT shipment_number FROM late_shipments");
    }

    @Test
    void aKeptResultAndARefusedPromptAreDecisionsWithoutAModel() {
        log.applied(lars, ActivityView.CLAIM_FORM, "Customer: Aalborg Trekking ApS");
        log.refused(
                lars,
                ActivityView.DASHBOARD,
                "A long question…",
                "A question can have at most 500 characters; this one has 600.");

        assertThat(rows(lars.visit()))
                .extracting(
                        ActivityEntryRepository.Row::decision,
                        ActivityEntryRepository.Row::prompt,
                        ActivityEntryRepository.Row::proposal,
                        ActivityEntryRepository.Row::rule,
                        ActivityEntryRepository.Row::model,
                        ActivityEntryRepository.Row::inputTokens)
                .containsExactlyInAnyOrder(
                        tuple(Decision.APPLIED, null, "Customer: Aalborg Trekking ApS", null, null, null),
                        tuple(
                                Decision.REJECTED,
                                "A long question…",
                                null,
                                "A question can have at most 500 characters; this one has 600.",
                                null,
                                null));
    }

    @Test
    void aVisitThatEndsTakesItsOwnRowsAlong() {
        var someoneElse = new ActivityLog.Author(UUID.randomUUID(), 8);
        log.asked(lars, ActivityView.DASHBOARD, "Open claims", "The schema");
        log.asked(someoneElse, ActivityView.DASHBOARD, "Late shipments", "The schema");

        log.forget(lars.visit());

        assertThat(rows(lars.visit())).isEmpty();
        assertThat(rows(someoneElse.visit()))
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Late shipments");
    }

    /** A turn can end after its session has: the answer has no row to go to, and nothing comes back. */
    @Test
    void aTurnWhoseVisitHasEndedStaysGone() {
        var turn = log.asked(lars, ActivityView.DASHBOARD, "Open claims", "The schema");
        log.forget(lars.visit());

        log.ended(turn, "A table of open claims.", "STOP", 900, 40);
        log.proposed(turn, "SELECT claim_number FROM claims");

        assertThat(entries.count()).isZero();
    }

    /** Every row of a visit, as the view reads them. */
    private List<ActivityEntryRepository.Row> rows(UUID visit) {
        return entries.search("", visit, null, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurredAt", "id")))
                .getContent();
    }
}
