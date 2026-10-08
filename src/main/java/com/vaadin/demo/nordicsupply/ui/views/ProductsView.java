package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;

/** The product catalogue by SKU with the list price of the dataset's day, read-only. */
@Route("products")
@PageTitle("Products")
@Menu(title = "Products", order = 5, icon = "icons/package.svg")
public class ProductsView extends PageView implements HasReadme {

    private static final String README = """
            **This view shows where the data for the Dashboard and Claims views come from**.

            It lists all the products this demo has in the database.

            The only thing you can do here is search for a particular product.
            """;

    public ProductsView(ProductRepository products) {
        super("products", "Products", "Manage Your Catalogue", "Products");
        var table = new DataTable<Row>(
                "Products", Sort.by("sku"), products::search, "Search SKU, name, supplier or category");
        table.text("SKU", Row::sku);
        table.text("Name", Row::name);
        table.text("Category", Row::category);
        table.text("Supplier", Row::supplier);
        table.text("Variant", Row::variant);
        table.money("List price (EUR)", Row::listPrice);
        table.flag("Active", Row::active);
        add(table);
    }

    @Override
    public String readme() {
        return README;
    }
}
