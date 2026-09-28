package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.ui.components.Readme;

/**
 * Each list view asks its repository for its rows in the order the view promises, aligns its amounts, and carries a
 * readme that names it and says what the list is.
 */
class ListViewsTest {

    @Test
    void ordersAreNewestFirst() {
        var orders = mock(SalesOrderRepository.class);
        when(orders.search(any(), any())).thenReturn(Page.empty());

        show(new OrdersView(orders));

        assertThat(sortAskedOf(orders)).isEqualTo(Sort.by(Sort.Direction.DESC, "placedAt", "id"));
    }

    @Test
    void customersAreByNameThenNumber() {
        var customers = mock(CustomerRepository.class);
        when(customers.search(any(), any())).thenReturn(Page.empty());

        show(new CustomersView(customers));

        assertThat(sortAskedOf(customers)).isEqualTo(Sort.by("name", "customerNumber"));
    }

    @Test
    void productsAreBySku() {
        var products = mock(ProductRepository.class);
        when(products.search(any(), any())).thenReturn(Page.empty());

        show(new ProductsView(products));

        assertThat(sortAskedOf(products)).isEqualTo(Sort.by("sku"));
    }

    @Test
    void eachListAlignsItsAmountsToTheEnd() {
        assertThat(numberColumns(new OrdersView(mock(SalesOrderRepository.class))))
                .containsExactly("Net (EUR)");
        assertThat(numberColumns(new CustomersView(mock(CustomerRepository.class))))
                .containsExactly("Terms (days)");
        assertThat(numberColumns(new ProductsView(mock(ProductRepository.class))))
                .containsExactly("List price (EUR)");
    }

    @Test
    void eachListCarriesAReadmeNamedAfterItThatSaysWhatItListsAndWhatTheSearchMatches() {
        var readmes = List.of(
                new OrdersView(mock(SalesOrderRepository.class)).readme(),
                new CustomersView(mock(CustomerRepository.class)).readme(),
                new ProductsView(mock(ProductRepository.class)).readme());

        assertThat(readmes).extracting(Readme::title).containsExactly("Orders", "Customers", "Products");
        assertThat(readmes)
                .extracting(Readme::body)
                .satisfiesExactly(
                        orders -> assertThat(orders).contains("one row per sales order", "Search by"),
                        customers -> assertThat(customers).contains("One row per retail customer", "Search by"),
                        products -> assertThat(products).contains("one row per product", "Search by"));
    }

    /** Fetches the first page the way the browser does when the view opens. */
    private static void show(Component view) {
        grid(view)
                .getDataProvider()
                .fetch(new Query<>(0, 50, List.of(), null, null))
                .toList();
    }

    private static List<String> numberColumns(Component view) {
        return grid(view).getColumns().stream()
                .filter(column -> column.getTextAlign() == ColumnTextAlign.END)
                .map(Grid.Column::getHeaderText)
                .toList();
    }

    private static Grid<?> grid(Component view) {
        return view.getChildren()
                .flatMap(Component::getChildren)
                .filter(Grid.class::isInstance)
                .map(c -> (Grid<?>) c)
                .findFirst()
                .orElseThrow();
    }

    private static Sort sortAskedOf(SalesOrderRepository orders) {
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).search(eq(""), page.capture());
        return page.getValue().getSort();
    }

    private static Sort sortAskedOf(CustomerRepository customers) {
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).search(eq(""), page.capture());
        return page.getValue().getSort();
    }

    private static Sort sortAskedOf(ProductRepository products) {
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(products).search(eq(""), page.capture());
        return page.getValue().getSort();
    }
}
