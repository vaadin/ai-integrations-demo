package com.vaadin.demo.nordicsupply.ui.views;

import java.util.List;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.DataTable.Col;

/** Sales orders, newest first, read-only. */
@Route("orders")
@PageTitle("Orders")
@Menu(title = "Orders", order = 3, icon = "icons/truck.svg")
public class OrdersView extends PageView {

    public OrdersView(SalesOrderRepository orders) {
        super("Orders", "Sales orders");
        var table = new DataTable<>(
                "Orders",
                List.of(
                        Col.text("Order", Row::orderNumber),
                        Col.text("Customer", Row::customer),
                        Col.text("Placed", row -> row.placedAt().toLocalDate()),
                        Col.text("Status", Row::status),
                        Col.text("Promised delivery", Row::promisedDeliveryDate),
                        Col.text("Channel", Row::channel),
                        Col.number("Net (EUR)", Row::totalNet)),
                Sort.by(Sort.Direction.DESC, "placedAt", "id"),
                orders::search,
                "Search order number, customer or status");
        add(table);
    }
}
