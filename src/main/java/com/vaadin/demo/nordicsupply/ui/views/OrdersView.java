package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;
import com.vaadin.demo.nordicsupply.ui.components.Readme;

/** Sales orders, newest first, read-only. */
@Route("orders")
@PageTitle("Orders")
@Menu(title = "Orders", order = 3, icon = "icons/truck.svg")
public class OrdersView extends PageView implements HasReadme {

    private static final Readme README = new Readme("Orders", """
            The order list every order desk has: one row per sales order with the customer, when it was placed, its
            status, the promised delivery date, the channel it came in through and the net value, newest first.
            Search by order number, customer name or status; the search matches anywhere in the text.
            """);

    public OrdersView(SalesOrderRepository orders) {
        super("Orders", "Sales orders");
        var table = new DataTable<Row>(
                "Orders",
                Sort.by(Sort.Direction.DESC, "placedAt", "id"),
                orders::search,
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
    public Readme readme() {
        return README;
    }
}
