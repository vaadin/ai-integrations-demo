package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.KeyMapper;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

import com.vaadin.demo.nordicsupply.config.DisplayLocale;
import com.vaadin.demo.nordicsupply.data.ActivityEntryRepository;
import com.vaadin.demo.nordicsupply.data.ActivityEntryRepository.Row;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.domain.ActivityEntry.Decision;
import com.vaadin.demo.nordicsupply.domain.ActivityView;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.TestUi;

/**
 * The log shows the rows of this visit, newest first, of every employee to the head office and of their own to an
 * account manager, each as the audit asks: when, who, where, what happened, the start of what was asked and what the
 * turn took, and in a row's details the texts too long for a cell, a refusal's rule, the model and how the turn ended.
 */
class ActivityLogViewTest {

    private static final SignedInUser HEAD_OFFICE = new SignedInUser(1, "Test User", Scopes.ALL);
    private static final SignedInUser LARS = new SignedInUser(8, "Lars Nygaard", Scopes.FI_EE);

    /** A turn's time in the log: the demo clock's, in Helsinki. */
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 7, 16, 3, 19);

    private final ActivityEntryRepository entries = mock(ActivityEntryRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final Visit visit = new Visit(mock(ActivityLog.class));

    /** Held here, since the current UI is kept only weakly and a garbage collection would clear it mid-test. */
    private UI ui;

    @BeforeEach
    void aVisitAsTheHeadOffice() {
        ui = TestUi.withSession();
        ui.setLocale(DisplayLocale.LOCALE);
        when(currentUser.get()).thenReturn(HEAD_OFFICE);
        when(entries.search(anyString(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void theColumnsAnswerTheAuditsQuestions() {
        assertThat(grid(view()).getColumns())
                .extracting(Grid.Column::getHeaderText)
                .containsExactly("When", "Who", "View", "Event", "Prompt", "Tokens in / out");
    }

    @Test
    void theHeadOfficeReadsEveryEmployeesRowsOfThisVisitNewestFirst() {
        show(view());

        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(entries).search(eq(""), eq(visit.id()), isNull(), page.capture());
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
    }

    @Test
    void anAccountManagerReadsTheirOwnRowsOfThisVisit() {
        when(currentUser.get()).thenReturn(LARS);

        show(view());

        verify(entries).search(eq(""), eq(visit.id()), eq(8), any());
    }

    @Test
    void aTurnIsShownWithItsViewItsPromptAndWhatItTook() {
        var turn = new Row(
                AT,
                "Test User",
                ActivityView.DASHBOARD,
                "Open claims",
                "The schema",
                "SELECT claim_number FROM claims",
                null,
                null,
                "gpt-5.6-luna",
                "STOP",
                4512,
                1830);

        assertThat(rendered(view(), turn))
                .containsExactly(
                        "2026-09-07 16:03:19", "Test User", "Dashboard", "AI turn", "Open claims", "4 512 / 1 830");
    }

    @Test
    void aMissingTokenCountIsAQuestionMark() {
        var turn = new Row(
                AT,
                "Test User",
                ActivityView.CLAIM_FORM,
                "Fill in",
                "The message",
                "Answer",
                null,
                null,
                "m",
                null,
                null,
                40);

        assertThat(rendered(view(), turn).getLast()).isEqualTo("? / 40");
    }

    @Test
    void aDecisionIsAnEventOfItsOwnAndItsDetailsSayWhatWasKeptOrWhichRuleRefused() {
        var kept = new Row(
                AT,
                "Test User",
                ActivityView.CLAIM_FORM,
                null,
                null,
                "Customer: Aalborg Trekking ApS",
                Decision.APPLIED,
                null,
                null,
                null,
                null,
                null);
        var refused = new Row(
                AT,
                "Test User",
                ActivityView.DASHBOARD,
                "A long question…",
                null,
                null,
                Decision.REJECTED,
                "A question can have at most 500 characters; this one has 600.",
                null,
                null,
                null,
                null);
        var view = view();

        assertThat(rendered(view, kept).get(3)).isEqualTo("Applied");
        assertThat(rendered(view, refused).get(3)).isEqualTo("Rejected");
        assertThat(details(view, kept)).containsOnly(entry("proposal", "Customer: Aalborg Trekking ApS"));
        assertThat(details(view, refused))
                .containsOnly(
                        entry("prompt", "A long question…"),
                        entry("rule", "A question can have at most 500 characters; this one has 600."));
    }

    /** The chats show their times on the dataset's day at the visitor's time of day; the log does too. */
    @Test
    void aTimeIsShownAsTheVisitorsChatsShowTheirs() {
        TestUi.inTimeZone(ui, "America/New_York");
        var early = new Row(
                AT.withHour(1),
                "Test User",
                ActivityView.DASHBOARD,
                "Open claims",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(rendered(view(), early).getFirst()).isEqualTo("2026-09-07 18:03:19");
    }

    /** The prompt's cell cuts it short; a row's details hold it whole, with what the model saw and proposed. */
    @Test
    void aRowsDetailsHoldTheTextsTooLongForACell() {
        var query =
                "SELECT c.claim_number, c.status\nFROM claims c\nWHERE c.status = 'OPEN' -- " + "and more ".repeat(40);
        var turn = new Row(
                AT,
                "Test User",
                ActivityView.DASHBOARD,
                "Open claims",
                "The database schema and today's date",
                query,
                null,
                null,
                "gpt-5.6-luna",
                "STOP",
                4512,
                1830);

        var view = view();

        assertThat(details(view, turn))
                .containsOnly(
                        entry("prompt", "Open claims"),
                        entry("modelCouldSee", "The database schema and today's date"),
                        entry("proposal", query),
                        entry("model", "gpt-5.6-luna"),
                        entry("finishReason", "STOP"));
    }

    @Test
    void aVisitWithNothingLoggedSaysWhatWillAppear() {
        assertThat(grid(view()).getEmptyStateText()).startsWith("Nothing yet.");
    }

    @Test
    void theReadmeSaysWhatTheLogRecordsWhereItComesFromAndWhoseRowsTheyAre() {
        var view = view();

        assertThat(view.readmeSections())
                .extracting(section -> section.content())
                .satisfiesExactly(
                        record -> assertThat(record).contains("left your network", "Applied", "Rejected"),
                        source -> assertThat(source).contains("request and response", "request interceptor"),
                        visit -> assertThat(visit).contains("deleted when it ends", "head office"));
        assertThat(view.readMore())
                .extracting(link -> link.href())
                .contains(
                        "https://vaadin.com/docs/latest/flow/ai-support/listeners",
                        "https://vaadin.com/docs/latest/flow/ai-support/request-interception");
    }

    private ActivityLogView view() {
        return new ActivityLogView(entries, currentUser, visit);
    }

    /** Asks the grid for its first page, as the browser does when the view is shown. */
    private static void show(ActivityLogView view) {
        grid(view)
                .getDataProvider()
                .fetch(new Query<>(0, 50, List.of(), null, null))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Grid<Row> grid(ActivityLogView view) {
        return (Grid<Row>) ComponentTree.descendants(view)
                .filter(Grid.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /**
     * The values a row's details are drawn from, by the names the template reads them by, in no particular order;
     * empty ones left out.
     */
    private static Map<String, String> details(ActivityLogView view, Row row) {
        var json = new ObjectMapper().createObjectNode();
        view.details()
                .render(new Element("div"), new KeyMapper<>())
                .getDataGenerator()
                .orElseThrow()
                .generateData(row, json);
        var details = new LinkedHashMap<String, String>();
        json.properties().forEach(property -> {
            var value = property.getValue();
            if (!value.isNull() && !value.asString().isEmpty()) {
                var name = property.getKey();
                details.put(name.substring(name.lastIndexOf('_') + 1), value.asString());
            }
        });
        return details;
    }

    /** What the grid's renderers write into a row's cells, column by column. */
    private static List<String> rendered(ActivityLogView view, Row row) {
        return grid(view).getColumns().stream()
                .map(column -> {
                    var json = new ObjectMapper().createObjectNode();
                    column.getRenderer()
                            .render(new Element("div"), new KeyMapper<>())
                            .getDataGenerator()
                            .orElseThrow()
                            .generateData(row, json);
                    return json.properties().iterator().next().getValue().asString();
                })
                .toList();
    }
}
