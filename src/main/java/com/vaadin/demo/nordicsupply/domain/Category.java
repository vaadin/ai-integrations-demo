package com.vaadin.demo.nordicsupply.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A product category of the catalogue.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    private Integer id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    protected Category() {}

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Category other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
