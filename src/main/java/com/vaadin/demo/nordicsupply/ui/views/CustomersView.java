package com.vaadin.demo.nordicsupply.ui.views;

import java.util.List;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.DataTable.Col;

/** The retail customers by name, read-only. */
@Route("customers")
@PageTitle("Customers")
@Menu(title = "Customers", order = 4, icon = "icons/square-user.svg")
public class CustomersView extends PageView {

    public CustomersView(CustomerRepository customers) {
        super("Customers", "Retail accounts");
        var table = new DataTable<>(
                "Customers",
                List.of(
                        Col.text("Number", Row::customerNumber),
                        Col.text("Name", Row::name),
                        Col.text("Chain", Row::chainName),
                        Col.text("Segment", Row::segment),
                        Col.text("City", Row::city),
                        Col.text("Country", Row::country),
                        Col.number("Terms (days)", Row::paymentTermsDays),
                        Col.text("Active", Row::active)),
                Sort.by("name", "customerNumber"),
                customers::search,
                "Search name, number or city");
        add(table);
    }
}
