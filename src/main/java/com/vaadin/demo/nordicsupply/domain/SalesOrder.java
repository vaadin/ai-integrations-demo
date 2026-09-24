package com.vaadin.demo.nordicsupply.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A sales order. The class is not called {@code Order} because ORDER is a reserved word in JPQL.
 */
@Entity
@Table(name = "orders")
public class SalesOrder {

    @Id
    private Integer id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "channel")
    private String channel;

    @Column(name = "status")
    private String status;

    @Column(name = "promised_ship_date")
    private LocalDate promisedShipDate;

    @Column(name = "promised_delivery_date")
    private LocalDate promisedDeliveryDate;

    @Column(name = "total_net")
    private BigDecimal totalNet;

    @Column(name = "customer_reference")
    private String customerReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    protected SalesOrder() {}

    public Integer getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public String getChannel() {
        return channel;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPromisedShipDate() {
        return promisedShipDate;
    }

    public LocalDate getPromisedDeliveryDate() {
        return promisedDeliveryDate;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public Customer getCustomer() {
        return customer;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SalesOrder other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
