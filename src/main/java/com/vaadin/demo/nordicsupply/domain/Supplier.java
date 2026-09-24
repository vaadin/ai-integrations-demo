package com.vaadin.demo.nordicsupply.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A supplier Nordic Supply buys from.
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    private Integer id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "active")
    private boolean active;

    protected Supplier() {}

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Supplier other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
