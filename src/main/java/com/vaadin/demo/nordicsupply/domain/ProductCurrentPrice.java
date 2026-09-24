package com.vaadin.demo.nordicsupply.domain;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** The database view of the price in force today, one row per product. */
@Entity
@Table(name = "product_current_prices")
@Immutable
public class ProductCurrentPrice {

    @Id
    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "sku")
    private String sku;

    @Column(name = "name")
    private String name;

    @Column(name = "list_price")
    private BigDecimal listPrice;

    protected ProductCurrentPrice() {}

    public Integer getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getListPrice() {
        return listPrice;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProductCurrentPrice other && Objects.equals(productId, other.getProductId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(productId);
    }
}
