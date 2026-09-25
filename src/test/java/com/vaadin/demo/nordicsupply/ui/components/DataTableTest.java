package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridNoneSelectionModel;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataChangeEvent;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
        return new DataTable<>(
                "Rows", List.of(DataTable.Col.text("Name", Row::name)), Sort.by("name"), this::fetch, "Search name");
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
    void numbersAlignToTheEndAndEverythingElseToTheStart() {
        var table = new DataTable<Row>(
                "Rows",
                List.of(
                        DataTable.Col.text("Name", Row::name),
                        DataTable.Col.number("Length", row -> row.name().length())),
                Sort.by("name"),
                this::fetch,
                "Search name");

        assertThat(grid(table).getColumns())
                .extracting(Grid.Column::getHeaderText, Grid.Column::getTextAlign)
                .containsExactly(tuple("Name", ColumnTextAlign.START), tuple("Length", ColumnTextAlign.END));
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
