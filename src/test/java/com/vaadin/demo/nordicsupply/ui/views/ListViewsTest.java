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
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;

/**
 * Each list view asks its repository for the rows of the signed-in user's region in the order the view promises,
 * aligns its amounts, and carries a readme that names it and says what the list is.
 */
class ListViewsTest {

    private static final SignedInUser LARS = new SignedInUser(8, "Lars Nygaard", Scopes.FI_EE);

    /** The session, signed in as Lars, whose region is Finland and Estonia. */
    private final CurrentUser currentUser = mock(CurrentUser.class);

    @BeforeEach
    void signedInAsLars() {
        when(currentUser.get()).thenReturn(LARS);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(LARS));
    }

    @Test
    void ordersAreTheRegionsNewestFirst() {
        var orders = mock(SalesOrderRepository.class);
        when(orders.search(any(), any(), any())).thenReturn(Page.empty());

        show(new OrdersView(orders, currentUser));

        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).search(eq(""), eq(List.of("FI", "EE")), page.capture());
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "placedAt", "id"));
    }

    @Test
    void customersAreTheRegionsByNameThenNumber() {
        var customers = mock(CustomerRepository.class);
        when(customers.search(any(), any(), any())).thenReturn(Page.empty());

        show(new CustomersView(customers, currentUser));

        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).search(eq(""), eq(List.of("FI", "EE")), page.capture());
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by("name", "customerNumber"));
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
        assertThat(numberColumns(new OrdersView(mock(SalesOrderRepository.class), currentUser)))
                .containsExactly("Net (EUR)");
        assertThat(numberColumns(new CustomersView(mock(CustomerRepository.class), currentUser)))
                .containsExactly("Terms (days)");
        assertThat(numberColumns(new ProductsView(mock(ProductRepository.class))))
                .containsExactly("List price (EUR)");
    }

    @Test
    void eachListCarriesAReadmeThatSaysWhatItListsAndWhatTheSearchMatches() {
        var readmes = List.of(
                new OrdersView(mock(SalesOrderRepository.class), currentUser).readme(),
                new CustomersView(mock(CustomerRepository.class), currentUser).readme(),
                new ProductsView(mock(ProductRepository.class)).readme());

        assertThat(readmes)
                .satisfiesExactly(
                        orders -> assertThat(orders).contains("all the orders", "search for a particular order"),
                        customers ->
                                assertThat(customers).contains("all the customers", "search for a particular customer"),
                        products ->
                                assertThat(products).contains("all the products", "search for a particular product"));
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

    private static Sort sortAskedOf(ProductRepository products) {
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(products).search(eq(""), page.capture());
        return page.getValue().getSort();
    }
}
