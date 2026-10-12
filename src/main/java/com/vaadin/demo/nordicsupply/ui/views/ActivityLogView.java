package com.vaadin.demo.nordicsupply.ui.views;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.config.DemoClock;
import com.vaadin.demo.nordicsupply.data.ActivityEntryRepository;
import com.vaadin.demo.nordicsupply.data.ActivityEntryRepository.Row;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;

/**
 * The activity log, the audit trail of the demo's AI: every turn a view sent to the model and every decision taken on
 * a result, newest first, from this visit alone. Within the visit an account manager reads their own rows and the
 * head office everyone's, the boundary of the lists drawn by the user, since a turn has an author and not a country.
 * The rail lists the view at its foot, apart from the views whose work it records. A row shows who did what where,
 * the start of its prompt and the tokens the turn took; a click opens its details with the rest.
 */
@Route("activity")
@PageTitle("Activity Log")
public class ActivityLogView extends PageView implements HasReadme {

    private static final List<ReadmeSection> README_SECTIONS = List.of(
            new ReadmeSection("Every AI Turn on Record", """
                    **See what left your network, what the AI model did with it, and who approved the result.**

                    Every question sent to the model is listed here, newest first: who asked and on which view, the
                    prompt, what else the model could see, what it proposed or answered, and the tokens it used.

                    Creating a claim from the form the AI filled in is recorded as **Applied**. A question the
                    application refuses, such as one that is too long, is recorded as **Rejected**, with the rule that
                    refused it.

                    Click a row to read the whole prompt, what the model could see and what it proposed. The search
                    finds words in the prompts and the proposals.
                    """),
            new ReadmeSection("Written by the Application", """
                    **The application writes every row** from the AI components' own hooks: the request and response
                    listeners, the controllers' change listeners and the request interceptor.

                    Nothing here is written by the AI model.
                    """),
            new ReadmeSection("Only Your Visit", """
                    **You see the rows of your own visit**, and they are deleted when it ends.

                    An account manager sees their own turns. The head office sees everyone's.
                    """));

    private static final List<Link> READ_MORE = List.of(
            new Link(
                    "Request & Response Listeners",
                    "https://vaadin.com/docs/latest/flow/ai-support/listeners",
                    "The hooks every turn is recorded from, with the finish reason and the token usage"),
            new Link(
                    "Request Interception",
                    "https://vaadin.com/docs/latest/flow/ai-support/request-interception",
                    "Where a prompt can be refused, or rewritten, before it goes out"),
            new Link(
                    "AI support in Vaadin Flow",
                    "https://vaadin.com/docs/latest/flow/ai-support",
                    "Overview of the AI support"));

    /** Token counts, grouped in the UI's locale. */
    private final NumberFormat counts = NumberFormat.getIntegerInstance(getLocale());

    public ActivityLogView(ActivityEntryRepository entries, CurrentUser currentUser, Visit visit) {
        super("activity-log", "Activity Log", "Trace Every AI Turn", "Activity");
        var user = currentUser.get();
        Integer only = Scopes.ALL.equals(user.scope()) ? null : user.id();
        var visitor = DemoClock.forVisitor(UI.getCurrent());
        var table = new DataTable<Row>(
                "Activity log",
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"),
                (term, page) -> entries.search(term, visit.id(), only, page),
                "Search prompts and proposals");
        table.dateTime("When", row -> shown(row.occurredAt(), visitor));
        table.text("Who", Row::who);
        table.text("View", row -> row.view().label());
        table.text("Event", ActivityLogView::event);
        table.longText("Prompt", Row::prompt);
        table.text("Tokens in / out", this::tokens);
        table.itemDetails(details());
        table.setEmptyStateText("Nothing yet. Every question to the AI, and every decision on its work, appears here.");
        add(table);
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
     * A row's time as the chats show theirs: on the dataset's day, at the visitor's time of day. The log keeps the
     * demo clock's time in Helsinki.
     */
    private static LocalDateTime shown(LocalDateTime helsinki, DemoClock visitor) {
        var instant = visitor.onTheDatasetsDay(
                helsinki.atZone(DemoClock.CLOCK.getZone()).toInstant());
        return LocalDateTime.ofInstant(instant, visitor.getZone());
    }

    /** What a row records: a turn of the AI, or a decision on its work, a person keeping it or a rule refusing it. */
    private static String event(Row row) {
        if (row.decision() == null) {
            return "AI turn";
        }
        return switch (row.decision()) {
            case APPLIED -> "Applied";
            case REJECTED -> "Rejected";
        };
    }

    /**
     * A row's details, shown below it when it is clicked: the texts too long for a cell, the prompt whole, what the
     * model could see and the proposal or answer as the AI wrote it, then the rule that refused a prompt, the model
     * and how the turn ended, which is almost always a plain stop. What a row has no value for is left out. A template the browser fills from the row's values, not a component per row: with
     * components, the grid left the rows below a row that opened or closed in their old places for a second or two.
     */
    LitRenderer<Row> details() {
        return LitRenderer.<Row>of("""
                        <dl class="activity-details">
                          ${item.prompt ? html`<dt>Prompt</dt><dd>${item.prompt}</dd>` : ''}
                          ${item.modelCouldSee ? html`<dt>Model could see</dt><dd>${item.modelCouldSee}</dd>` : ''}
                          ${item.proposal ? html`<dt>Proposal or answer</dt><dd><pre>${item.proposal}</pre></dd>` : ''}
                          ${item.rule ? html`<dt>Rule</dt><dd>${item.rule}</dd>` : ''}
                          ${item.model ? html`<dt>Model</dt><dd>${item.model}</dd>` : ''}
                          ${item.finishReason ? html`<dt>Finish reason</dt><dd>${item.finishReason}</dd>` : ''}
                        </dl>""")
                .withProperty("prompt", Row::prompt)
                .withProperty("modelCouldSee", Row::modelCouldSee)
                .withProperty("proposal", Row::proposal)
                .withProperty("rule", Row::rule)
                .withProperty("model", Row::model)
                .withProperty("finishReason", Row::finishReason);
    }

    /** The tokens a turn took, in and out, grouped as the lists group numbers; a decision took none. */
    private String tokens(Row row) {
        if (row.inputTokens() == null && row.outputTokens() == null) {
            return "";
        }
        return count(row.inputTokens()) + " / " + count(row.outputTokens());
    }

    private String count(Integer tokens) {
        return tokens == null ? "?" : counts.format(tokens);
    }
}
