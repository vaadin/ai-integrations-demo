package com.vaadin.demo.nordicsupply.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A retail customer of the wholesaler.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private Integer id;

    @Column(name = "customer_number")
    private String customerNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "chain_name")
    private String chainName;

    @Column(name = "segment")
    private String segment;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "email_domain")
    private String emailDomain;

    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(name = "active")
    private boolean active;

    protected Customer() {}

    public Integer getId() {
        return id;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public String getName() {
        return name;
    }

    public String getChainName() {
        return chainName;
    }

    public String getSegment() {
        return segment;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public Integer getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Customer other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
