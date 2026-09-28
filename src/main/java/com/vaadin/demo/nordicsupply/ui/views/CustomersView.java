package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository.Row;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;

/** The retail customers of the signed-in user's region by name, read-only. */
@Route("customers")
@PageTitle("Customers")
@Menu(title = "Customers", order = 4, icon = "icons/square-user.svg")
public class CustomersView extends PageView implements HasReadme {

    private static final String README = """
            One row per retail customer in your region: shops, chains, department stores, online stores, rental
            outfitters, clubs and schools, in alphabetical order, with whether the account is active. Search by name,
            customer number or city; the search matches anywhere in the text.
            """;

    public CustomersView(CustomerRepository customers, CurrentUser currentUser) {
        super("Customers", "Retail accounts");
        var countries = currentUser.get().scope().countries();
        var table = new DataTable<Row>(
                "Customers",
                Sort.by("name", "customerNumber"),
                (term, page) -> customers.search(term, countries, page),
                "Search name, number or city");
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

    @Override
    public String readme() {
        return README;
    }
}
