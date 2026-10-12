package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Activity Log view reads the rows of one visit, newest first, with the employee's name, of one employee or of
 * every one, and matching a search on the prompt or the proposal.
 */
@SpringBootTest
@Transactional
class ActivityEntryRepositoryTest {

    /** The order the view asks for. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt", "id");

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 7, 9, 0);

    @Autowired
    private ActivityEntryRepository entries;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID visit = UUID.randomUUID();

    @Test
    void aVisitStartsWithNothing() {
        assertThat(search("", null).getTotalElements()).isZero();
    }

    @Test
    void aVisitReadsOnlyItsOwnRows() {
        logged(visit, NINE, 1, "Open claims", null);
        logged(UUID.randomUUID(), NINE, 1, "Somebody else's question", null);

        assertThat(search("", null).getContent())
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Open claims");
    }

    @Test
    void rowsComeNewestFirstWithTheEmployeesName() {
        logged(visit, NINE, 1, "Late shipments last month", null);
        logged(visit, NINE.plusMinutes(5), 8, "Open claims", null);

        assertThat(search("", null).getContent())
                .extracting(ActivityEntryRepository.Row::who, ActivityEntryRepository.Row::prompt)
                .containsExactly(tuple("Lars Nygaard", "Open claims"), tuple("Test User", "Late shipments last month"));
    }

    /** The grid pages through the log, so the order must be total: rows of one second go latest written first. */
    @Test
    void rowsOfTheSameSecondComeLatestWrittenFirst() {
        logged(visit, NINE, 1, "Written first", null);
        logged(visit, NINE, 1, "Written second", null);

        assertThat(search("", null).getContent())
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Written second", "Written first");
    }

    @Test
    void anEmployeeReadsTheirOwnRowsAndNobodyElses() {
        logged(visit, NINE, 1, "The head office's question", null);
        logged(visit, NINE.plusMinutes(1), 8, "Lars's question", null);

        assertThat(search("", 8).getContent())
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Lars's question");
        assertThat(search("", null).getTotalElements()).isEqualTo(2);
    }

    @Test
    void theSearchFindsWordsInThePromptOrTheProposalInAnyCase() {
        logged(visit, NINE, 1, "Open claims", "SELECT claim_number FROM claims");
        logged(visit, NINE.plusMinutes(1), 1, "Late shipments", "SELECT shipment_number FROM late_shipments");
        logged(visit, NINE.plusMinutes(2), 1, null, "Customer: Aalborg Trekking ApS");

        assertThat(search(SearchTerms.like("CLAIM"), null).getContent())
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Open claims");
        assertThat(search(SearchTerms.like("aalborg"), null).getContent())
                .extracting(ActivityEntryRepository.Row::proposal)
                .containsExactly("Customer: Aalborg Trekking ApS");
    }

    @Test
    void aPageIsThePageAskedForAndTheTotalCountsEveryRow() {
        logged(visit, NINE, 1, "Oldest", null);
        logged(visit, NINE.plusMinutes(5), 1, "Middle", null);
        logged(visit, NINE.plusMinutes(10), 1, "Newest", null);

        var page = entries.search("", visit, null, PageRequest.of(1, 1, NEWEST_FIRST));

        assertThat(page.getContent())
                .extracting(ActivityEntryRepository.Row::prompt)
                .containsExactly("Middle");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void aVisitsRowsAreDeletedAndNoOtherVisits() {
        var other = UUID.randomUUID();
        logged(visit, NINE, 1, "Open claims", null);
        logged(other, NINE, 1, "Somebody else's question", null);

        entries.deleteByVisit(visit);

        assertThat(search("", null).getTotalElements()).isZero();
        assertThat(entries.search("", other, null, PageRequest.of(0, 10, NEWEST_FIRST))
                        .getTotalElements())
                .isEqualTo(1);
    }

    private Page<ActivityEntryRepository.Row> search(String term, Integer userId) {
        return entries.search(term, visit, userId, PageRequest.of(0, 10, NEWEST_FIRST));
    }

    private void logged(UUID visit, LocalDateTime at, int userId, String prompt, String proposal) {
        jdbc.update(
                "insert into activity_log (visit_id, occurred_at, user_id, view_name, prompt, proposal)"
                        + " values (?, ?, ?, 'DASHBOARD', ?, ?)",
                visit,
                at,
                userId,
                prompt,
                proposal);
    }
}
