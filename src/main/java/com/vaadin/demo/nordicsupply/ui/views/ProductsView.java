package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.HasReadme;
import com.vaadin.demo.nordicsupply.ui.components.Readme;

/** The product catalogue by SKU with the list price of the dataset's day, read-only. */
@Route("products")
@PageTitle("Products")
@Menu(title = "Products", order = 5, icon = "icons/package.svg")
public class ProductsView extends PageView implements HasReadme {

    private static final Readme README = new Readme("Products", """
            The catalogue: one row per product with its category, supplier, variant, whether it is still sold, and the
            list price as of 7 September 2026, the day the demo's data ends. Prices are kept as dated rows, so a price
            change is a new row from a date, never an edit. Search by SKU, name, supplier or category; the search
            matches anywhere in the text.
            """);

    public ProductsView(ProductRepository products) {
        super("Products", "Catalogue");
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
    public Readme readme() {
        return README;
    }
}
