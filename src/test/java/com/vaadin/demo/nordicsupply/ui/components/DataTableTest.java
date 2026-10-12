package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridNoneSelectionModel;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataChangeEvent;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.KeyMapper;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

import com.vaadin.demo.nordicsupply.config.DisplayLocale;

/**
 * The table asks its query for the page the grid shows, in the view's sort, with the search box's text as the term,
 * and shows what the query answers.
 */
class DataTableTest {

    private record Row(String name) {}

    private final List<Row> rows =
            IntStream.rangeClosed(1, 120).mapToObj(i -> new Row("Row " + i)).toList();
    private final List<String> terms = new ArrayList<>();
    private final List<Pageable> pages = new ArrayList<>();

    /**
     * The table takes its locale from the UI it is built in; held here, since the current UI is kept only weakly
     * and a garbage collection would otherwise clear it mid-test.
     */
    private final UI ui = new UI();

    @BeforeEach
    void aUiInTheDisplayLocale() {
        ui.setLocale(DisplayLocale.LOCALE);
        UI.setCurrent(ui);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    /** A query over 120 rows that records what it is asked and filters like the repositories do. */
    private Page<Row> fetch(String term, Pageable page) {
        terms.add(term);
        pages.add(page);
        var contains = term.replace("%", "");
        var matching = rows.stream()
                .filter(row -> row.name().toLowerCase().contains(contains))
                .toList();
        var from = (int) page.getOffset();
        var to = Math.min(from + page.getPageSize(), matching.size());
        return new PageImpl<>(matching.subList(from, to), page, matching.size());
    }

    private DataTable<Row> table() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.text("Name", Row::name);
        return table;
    }

    @Test
    void theCountIsTheQuerysTotalAskedWithOneUnsortedRow() {
        var count = provider(table()).size(query(0, 50));

        assertThat(count).isEqualTo(120);
        assertThat(terms).containsExactly("");
        assertThat(pages).singleElement().satisfies(page -> {
            assertThat(page.getPageSize()).isEqualTo(1);
            assertThat(page.getSort().isUnsorted()).isTrue();
        });
    }

    @Test
    void aPageIsFetchedAsTheGridsPageInTheViewsSort() {
        var shown = provider(table()).fetch(query(50, 50)).toList();

        assertThat(shown).hasSize(50).first().isEqualTo(new Row("Row 51"));
        assertThat(terms).containsExactly("");
        assertThat(pages).singleElement().satisfies(page -> {
            assertThat(page.getPageNumber()).isEqualTo(1);
            assertThat(page.getPageSize()).isEqualTo(50);
            assertThat(page.getSort()).isEqualTo(Sort.by("name"));
        });
    }

    @Test
    void theSearchBoxsTextReachesTheQueryAsALikeTermAndNarrowsTheRows() {
        var table = table();

        search(table).setValue(" Row 12 ");
        var shown = provider(table).fetch(query(0, 50)).toList();

        assertThat(shown).containsExactly(new Row("Row 12"), new Row("Row 120"));
        assertThat(terms).last().isEqualTo("%row 12%");
    }

    @Test
    void clearingTheSearchBoxShowsEveryRowAgain() {
        var table = table();
        search(table).setValue("Row 12");

        search(table).clear();
        var count = provider(table).size(query(0, 50));

        assertThat(count).isEqualTo(120);
        assertThat(terms).last().isEqualTo("");
    }

    @Test
    void aChangedSearchTellsTheGridToFetchAgain() {
        var table = table();
        var refreshes = new ArrayList<DataChangeEvent<Row>>();
        provider(table).addDataProviderListener(refreshes::add);

        search(table).setValue("Row 12");

        assertThat(refreshes).hasSize(1);
    }

    @Test
    void theGridIsReadOnlyNamedAndSaysWhenNothingMatches() {
        var grid = grid(table());

        assertThat(grid.getSelectionModel()).isInstanceOf(GridNoneSelectionModel.class);
        assertThat(grid.getAriaLabel()).contains("Rows");
        assertThat(grid.getEmptyStateText()).isEqualTo("No matches");
    }

    @Test
    void amountsAndCountsAlignToTheEndAndEverythingElseToTheStart() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.text("Name", Row::name);
        table.date("Since", row -> LocalDate.of(2026, 8, 28));
        table.money("Net", row -> new BigDecimal("9034.5"));
        table.number("Length", row -> row.name().length());
        table.flag("Named", row -> true);

        assertThat(grid(table).getColumns())
                .extracting(Grid.Column::getHeaderText, Grid.Column::getTextAlign)
                .containsExactly(
                        tuple("Name", ColumnTextAlign.START),
                        tuple("Since", ColumnTextAlign.START),
                        tuple("Net", ColumnTextAlign.END),
                        tuple("Length", ColumnTextAlign.END),
                        tuple("Named", ColumnTextAlign.START));
    }

    @Test
    void amountsAreGroupedWithTwoDecimalsInTheUisLocaleAndDatesStayIso() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.money("Net", row -> new BigDecimal("9034.5"));
        table.number("Count", row -> 12426);
        table.date("Since", row -> LocalDate.of(2026, 8, 28));

        assertThat(rendered(table, new Row("Row 1"))).containsExactly("9\u00a0034,50", "12\u00a0426", "2026-08-28");
    }

    @Test
    void timesAreWrittenToTheSecondAsTheDatesAre() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.dateTime("When", row -> LocalDateTime.of(2026, 9, 7, 14, 3, 12));

        assertThat(rendered(table, new Row("Row 1"))).containsExactly("2026-09-07 14:03:12");
    }

    /**
     * A long text's column starts narrow and takes the room the other columns leave, which keep their content's
     * width, wherever it stands among them; the user can still widen it. The whole text is the row details' to show.
     */
    @Test
    void aLongTextsColumnTakesTheRoomTheOthersLeave() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.text("Name", Row::name);
        table.longText("Note", row -> row.name() + " " + "and more ".repeat(40));
        table.text("Also", Row::name);

        var columns = grid(table).getColumns();

        assertThat(columns).extracting(Grid.Column::getFlexGrow).containsExactly(0, 1, 0);
        assertThat(columns.get(1).isAutoWidth()).isFalse();
        assertThat(columns.get(1).getWidth()).isEqualTo("10rem");
        assertThat(columns.get(1).isResizable()).isTrue();
    }

    /** Without a long text, the columns share the room left over, as the back office's lists do. */
    @Test
    void columnsOfShortTextsShareTheRoomLeft() {
        var table = new DataTable<Row>("Rows", Sort.by("name"), this::fetch, "Search name");
        table.text("Name", Row::name);
        table.text("Also", Row::name);

        assertThat(grid(table).getColumns())
                .extracting(Grid.Column::getFlexGrow)
                .containsExactly(1, 1);
    }

    /** A table can start empty, as the activity log does, and say why; a search that finds nothing says that. */
    @Test
    void anEmptyTableSaysWhatItWasToldUntilASearchFindsNothing() {
        var table = table();
        table.setEmptyStateText("Nothing yet");

        assertThat(grid(table).getEmptyStateText()).isEqualTo("Nothing yet");
        search(table).setValue("Row 1000");
        assertThat(grid(table).getEmptyStateText()).isEqualTo("No matches");
        search(table).clear();
        assertThat(grid(table).getEmptyStateText()).isEqualTo("Nothing yet");
    }

    /** What the grid's renderers write into a row's cells, column by column. */
    private static List<String> rendered(DataTable<Row> table, Row row) {
        return grid(table).getColumns().stream()
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

    private static Query<Row, Void> query(int offset, int limit) {
        return new Query<>(offset, limit, List.of(), null, null);
    }

    @SuppressWarnings("unchecked")
    private static Grid<Row> grid(DataTable<Row> table) {
        return (Grid<Row>)
                table.getChildren().filter(Grid.class::isInstance).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static DataProvider<Row, Void> provider(DataTable<Row> table) {
        return (DataProvider<Row, Void>) grid(table).getDataProvider();
    }

    private static TextField search(DataTable<Row> table) {
        return (TextField) table.getChildren()
                .filter(TextField.class::isInstance)
                .findFirst()
                .orElseThrow();
    }
}
