package com.vaadin.demo.nordicsupply.ui.views;

import java.util.List;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository.Row;
import com.vaadin.demo.nordicsupply.ui.components.DataTable;
import com.vaadin.demo.nordicsupply.ui.components.DataTable.Col;

/** The product catalogue by SKU with the list price of the dataset's day, read-only. */
@Route("products")
@PageTitle("Products")
@Menu(title = "Products", order = 5, icon = "icons/package.svg")
public class ProductsView extends PageView {

    public ProductsView(ProductRepository products) {
        super("Products", "Catalogue");
        var table = new DataTable<>(
                "Products",
                List.of(
                        Col.text("SKU", Row::sku),
                        Col.text("Name", Row::name),
                        Col.text("Category", Row::category),
                        Col.text("Supplier", Row::supplier),
                        Col.text("Variant", Row::variant),
                        Col.number("List price (EUR)", Row::listPrice),
                        Col.text("Active", Row::active)),
                Sort.by("sku"),
                products::search,
                "Search SKU, name, supplier or category");
        add(table);
    }
}
