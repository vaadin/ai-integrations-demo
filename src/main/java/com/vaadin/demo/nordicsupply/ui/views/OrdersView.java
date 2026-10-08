package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository.Row;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;

/** The sales orders of the signed-in user's region, newest first, read-only. */
@Route("orders")
@PageTitle("Orders")
@Menu(title = "Orders", order = 3, icon = "icons/truck.svg")
public class OrdersView extends PageView implements HasReadme {

    private static final String README = """
            **This view shows where the data for the Dashboard and Claims views come from**.

            It lists all the orders this demo has in the database.

            The only thing you can do here is search for a particular order.
            """;

    public OrdersView(SalesOrderRepository orders, CurrentUser currentUser) {
        super("orders", "Orders", "Keep Orders Moving", "Orders");
        var countries = currentUser.get().scope().countries();
        var table = new DataTable<Row>(
                "Orders",
                Sort.by(Sort.Direction.DESC, "placedAt", "id"),
                (term, page) -> orders.search(term, countries, page),
                "Search order number, customer or status");
        table.text("Order", Row::orderNumber);
        table.text("Customer", Row::customer);
        table.date("Placed", row -> row.placedAt().toLocalDate());
        table.text("Status", Row::status);
        table.date("Promised delivery", Row::promisedDeliveryDate);
        table.text("Channel", Row::channel);
        table.money("Net (EUR)", Row::totalNet);
        add(table);
    }

    @Override
    public String readme() {
        return README;
    }
}
