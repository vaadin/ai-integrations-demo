package com.vaadin.demo.nordicsupply.data;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.Product;

/** Products: the list view and the active-products tile. */
public interface ProductRepository extends JpaRepository<Product, Integer> {

    long countByActiveTrue();

    /** Rows of the Products list; the list price is the one in force today, from the current-price view. */
    record Row(
            String sku,
            String name,
            String category,
            String supplier,
            String variant,
            BigDecimal listPrice,
            boolean active) {}

    /**
     * The rows matching a {@link SearchTerms#like} term on SKU, name, supplier or category; every row for
     * {@code ""}.
     */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.ProductRepository$Row(
                p.sku, p.name, k.name, s.name, p.variant, cp.listPrice, p.active)
            from Product p join p.category k join p.supplier s
            left join ProductCurrentPrice cp on cp.productId = p.id
            where :term = '' or lower(p.sku) like :term or lower(p.name) like :term
               or lower(s.name) like :term or lower(k.name) like :term
            """)
    Page<Row> search(@Param("term") String term, Pageable pageable);
}
