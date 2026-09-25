package com.vaadin.demo.nordicsupply.ui.components;

import java.util.List;
import java.util.function.BiFunction;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.SearchTerms;
import com.vaadin.demo.nordicsupply.util.Formats;

/**
 * A read-only, lazily loaded grid over a repository query with a search box: the Orders, Customers and Products
 * views of the back office. The view says which columns to show, how the rows are sorted and which repository
 * method fetches a page; the search text reaches the query as a parameter, never as SQL.
 */
public class DataTable<T> extends VerticalLayout {

    /** One grid column: its header, the value it reads from a row, and where the text sits in the cell. */
    public record Col<T>(String header, ValueProvider<T, Object> value, ColumnTextAlign align) {

        /** A column of text, dates or flags, aligned to the start. */
        public static <T> Col<T> text(String header, ValueProvider<T, Object> value) {
            return new Col<>(header, value, ColumnTextAlign.START);
        }

        /** A column of amounts or counts, aligned to the end so the digits line up. */
        public static <T> Col<T> number(String header, ValueProvider<T, Object> value) {
            return new Col<>(header, value, ColumnTextAlign.END);
        }
    }

    private final Grid<T> grid = new Grid<>();
    private final TextField search = new TextField();

    /**
     * @param name what the rows are, as the grid's accessible name
     * @param columns the columns, in order
     * @param sort the order of the rows; a stable one, so that paging never shows a row twice
     * @param fetch the repository query: a {@link SearchTerms#like} term and the page to load
     * @param searchPlaceholder what the search box says it searches
     */
    public DataTable(
            String name,
            List<Col<T>> columns,
            Sort sort,
            BiFunction<String, Pageable, Page<T>> fetch,
            String searchPlaceholder) {
        addClassName("data-table");
        setPadding(false);
        search.setPlaceholder(searchPlaceholder);
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> {
            grid.getDataProvider().refreshAll();
            grid.scrollToStart();
            grid.recalculateColumnWidths();
        });
        for (var column : columns) {
            grid.addColumn(row -> Formats.value(column.value().apply(row)))
                    .setHeader(column.header())
                    .setTextAlign(column.align())
                    .setAutoWidth(true)
                    .setResizable(true);
        }
        grid.setAriaLabel(name);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setEmptyStateText("No matches");
        // the grid's page becomes a Spring pageable; the count needs no sort, so it asks for one unsorted row
        grid.setItemsPageable(
                page -> fetch.apply(term(), PageRequest.of(page.getPageNumber(), page.getPageSize(), sort))
                        .getContent(),
                page -> fetch.apply(term(), PageRequest.of(0, 1)).getTotalElements());
        add(search, grid);
        expand(grid);
    }

    private String term() {
        return SearchTerms.like(search.getValue());
    }
}
