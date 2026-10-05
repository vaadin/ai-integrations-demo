package com.vaadin.demo.nordicsupply.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The two lines a widget reply carries are read out of it, however the model wrote them. */
class WidgetReplyTest {

    @Test
    void readsTitleAndSummary() {
        var reply = WidgetReply.parse("""
                TITLE: Late shipments, August 2026
                SUMMARY: Shipments dispatched after the promised date, per week.

                Counted from late_shipments.
                """);
        assertThat(reply.title()).isEqualTo("Late shipments, August 2026");
        assertThat(reply.summary()).isEqualTo("Shipments dispatched after the promised date, per week.");
        assertThat(reply.rest()).isEqualTo("Counted from late_shipments.");
    }

    @Test
    void readsThemThroughMarkdownBold() {
        var reply = WidgetReply.parse("**TITLE:** Open claims\n**SUMMARY:** Claims not yet resolved.\n");
        assertThat(reply.title()).isEqualTo("Open claims");
        assertThat(reply.summary()).isEqualTo("Claims not yet resolved.");
    }

    /** Markdown bold as models write it: around the label and the colon, around the label alone, or the whole line. */
    @Test
    void readsThemThroughEveryFormOfBold() {
        assertThat(WidgetReply.parse("**TITLE**: Open claims\n**SUMMARY**: Claims not yet resolved.")
                        .title())
                .isEqualTo("Open claims");
        assertThat(WidgetReply.parse("**TITLE: Open claims**\n**SUMMARY: Claims not yet resolved.**"))
                .extracting(WidgetReply::title, WidgetReply::summary)
                .containsExactly("Open claims", "Claims not yet resolved.");
    }

    /**
     * A streamed reply begins with what the model wrote before and between its tool calls, and the title line can
     * follow that text on the same line.
     */
    @Test
    void readsTheLinesAfterWhatTheModelWroteWhileCallingTools() {
        var reply = WidgetReply.parse("I'll look at the schema first.Now I'll run the query:TITLE: Late shipments by"
                + " customer\n\nSUMMARY: Orders shipped after the promised date.\n\nRead as August 2026.");

        assertThat(reply.title()).isEqualTo("Late shipments by customer");
        assertThat(reply.summary()).isEqualTo("Orders shipped after the promised date.");
        assertThat(reply.rest()).isEqualTo("Read as August 2026.");
    }

    @Test
    void anEmptyTitleLineIsAnEmptyTitleNotTheNextLine() {
        var reply = WidgetReply.parse("TITLE:\nSUMMARY: Claims not yet resolved.");

        assertThat(reply.title()).isEmpty();
        assertThat(reply.summary()).isEqualTo("Claims not yet resolved.");
    }

    @Test
    void quotesListMarksAndHeadingsAreNotPartOfTheValue() {
        assertThat(WidgetReply.parse("TITLE: \"Late shipments, August 2026\"").title())
                .isEqualTo("Late shipments, August 2026");
        assertThat(WidgetReply.parse("- TITLE: Open claims\n- SUMMARY: Claims.\n\nA note.")
                        .rest())
                .isEqualTo("A note.");
        assertThat(WidgetReply.parse("## TITLE: Open claims\nSUMMARY: Claims.").title())
                .isEqualTo("Open claims");
    }

    @Test
    void missingPartsComeBackEmpty() {
        var reply = WidgetReply.parse("Here is the table you asked for.");
        assertThat(reply.title()).isEmpty();
        assertThat(reply.summary()).isEmpty();
        assertThat(reply.rest()).isEqualTo("Here is the table you asked for.");
    }

    @Test
    void restExcludesBothLines() {
        var reply = WidgetReply.parse("TITLE: A\nSUMMARY: B\nthe rest\nTITLE: not again\n");
        assertThat(reply.rest()).isEqualTo("the rest");
    }

    /** Prose says "title:" and "summary:" in lower case; only a label at the start of a line, or in capitals, is a line. */
    @Test
    void proseThatSaysTitleOrSummaryWithAColonStaysInTheNote() {
        var reply = WidgetReply.parse("TITLE: Open claims\nSUMMARY: Claims not yet resolved.\n\n"
                + "In summary: read as the open ones. Grouped by job title: buyers first.");

        assertThat(reply.title()).isEqualTo("Open claims");
        assertThat(reply.summary()).isEqualTo("Claims not yet resolved.");
        assertThat(reply.rest()).isEqualTo("In summary: read as the open ones. Grouped by job title: buyers first.");
    }

    @Test
    void narrationThatSaysTitleOrSummaryWithAColonDoesNotNameTheWidget() {
        var reply = WidgetReply.parse("Let me pick a title: something short. A summary: later.\n"
                + "TITLE: Open claims\nSUMMARY: Claims not yet resolved.\n\nA note.");

        assertThat(reply.title()).isEqualTo("Open claims");
        assertThat(reply.summary()).isEqualTo("Claims not yet resolved.");
        assertThat(reply.rest()).isEqualTo("A note.");
    }

    @Test
    void aTitleJoinedToTheNarrationWithoutPunctuationIsStillRead() {
        var reply = WidgetReply.parse("Let me run the queryTITLE: Late shipments\nSUMMARY: Shipments after the date.");

        assertThat(reply.title()).isEqualTo("Late shipments");
        assertThat(reply.summary()).isEqualTo("Shipments after the date.");
    }

    @Test
    void onlyBoldAndQuotesAroundAWholeValueAreTakenOff() {
        assertThat(WidgetReply.parse("TITLE: **Late** shipments").title()).isEqualTo("Late shipments");
        assertThat(WidgetReply.parse("TITLE: \"Late\" orders").title()).isEqualTo("\"Late\" orders");
        assertThat(WidgetReply.parse("TITLE: Customers' orders'").title()).isEqualTo("Customers' orders'");
    }

    /** A model that ignores the capitals it was asked for still gets its lines read, as long as they start a line. */
    @Test
    void aLabelAtTheStartOfALineIsReadInAnyCase() {
        var reply = WidgetReply.parse("Title: Open claims\nsummary: Claims not yet resolved.\n\nA note.");

        assertThat(reply.title()).isEqualTo("Open claims");
        assertThat(reply.summary()).isEqualTo("Claims not yet resolved.");
        assertThat(reply.rest()).isEqualTo("A note.");
    }

    @Test
    void ofTwoTitlesTheFirstIsRead() {
        var reply = WidgetReply.parse("TITLE: First\nSUMMARY: One.\nTITLE: Second\nSUMMARY: Two.\n");

        assertThat(reply.title()).isEqualTo("First");
        assertThat(reply.summary()).isEqualTo("One.");
    }
}
