package com.vaadin.demo.nordicsupply.ui.views;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;

/** The product catalogue by SKU with the list price of the dataset's day, read-only. */
@Route("products")
@PageTitle("Products")
@Menu(title = "Products", order = 5, icon = "icons/package.svg")
public class ProductsView extends PageView {

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
}
