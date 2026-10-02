package com.vaadin.demo.nordicsupply.ui.components;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BiFunction;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.data.renderer.NumberRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.SearchTerms;

/**
 * A read-only, lazily loaded grid over a repository query with a search box: the Orders, Customers and Products
 * views of the back office. The view says how the rows are sorted and which repository method fetches a page, then
 * adds its columns by type: text, a date, an amount, a count or a flag. Amounts and counts are written by the grid's
 * own renderers in the component's locale, the UI's, read once: the application offers one locale, so it never
 * changes. They are aligned to the end; dates stay ISO. The search text reaches the query as a parameter, never as
 * SQL.
 */
public class DataTable<T> extends VerticalLayout {

    private final Grid<T> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Locale locale = getLocale();

    /**
     * @param name what the rows are, as the grid's accessible name
     * @param sort the order of the rows; a stable one, so that paging never shows a row twice
     * @param fetch the repository query: a {@link SearchTerms#like} term and the page to load
     * @param searchPlaceholder what the search box says it searches
     */
    public DataTable(String name, Sort sort, BiFunction<String, Pageable, Page<T>> fetch, String searchPlaceholder) {
        addClassNames("data-table", "aura-surface");
        setPadding(false);
        setSpacing(false);
        setWidth(null);
        search.setPlaceholder(searchPlaceholder);
        search.setWidth("360px");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> {
            grid.getDataProvider().refreshAll();
            grid.scrollToStart();
            grid.recalculateColumnWidths();
        });
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

    /** A column of text, as it is in the row. */
    public void text(String header, ValueProvider<T, String> value) {
        column(grid.addColumn(value), header);
    }

    /** A column of dates, written ISO: {@code 2026-08-28}. */
    public void date(String header, ValueProvider<T, LocalDate> value) {
        column(grid.addColumn(new LocalDateRenderer<>(value, () -> DateTimeFormatter.ISO_LOCAL_DATE)), header);
    }

    /** A column of amounts: grouped, always two decimals, aligned to the end. The currency is in the header. */
    public void money(String header, ValueProvider<T, Number> value) {
        var format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        column(grid.addColumn(new NumberRenderer<>(value, format)), header).setTextAlign(ColumnTextAlign.END);
    }

    /** A column of counts: grouped, no decimals, aligned to the end. */
    public void number(String header, ValueProvider<T, Number> value) {
        column(grid.addColumn(new NumberRenderer<>(value, NumberFormat.getIntegerInstance(locale))), header)
                .setTextAlign(ColumnTextAlign.END);
    }

    /** A column of flags, written Yes or No. */
    public void flag(String header, ValueProvider<T, Boolean> value) {
        column(grid.addColumn(row -> value.apply(row) ? "Yes" : "No"), header);
    }

    private Grid.Column<T> column(Grid.Column<T> column, String header) {
        return column.setHeader(header).setAutoWidth(true).setResizable(true);
    }

    private String term() {
        return SearchTerms.like(search.getValue());
    }
}
