package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;

/** The retail customers by name, read-only. */
@Route("customers")
@PageTitle("Customers")
@Menu(title = "Customers", order = 4, icon = "icons/square-user.svg")
public class CustomersView extends PageView {

    public CustomersView(CustomerRepository customers) {
        super("Customers", "Retail accounts");
        var table = new DataTable<Row>(
                "Customers", Sort.by("name", "customerNumber"), customers::search, "Search name, number or city");
        table.text("Number", Row::customerNumber);
        table.text("Name", Row::name);
        table.text("Chain", Row::chainName);
        table.text("Segment", Row::segment);
        table.text("City", Row::city);
        table.text("Country", Row::country);
        table.number("Terms (days)", Row::paymentTermsDays);
        table.flag("Active", Row::active);
        add(table);
    }
}
