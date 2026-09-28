package com.vaadin.demo.nordicsupply.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** One row of the dataset's {@code staff} view: an employee, with the name the demo signs in as. */
@Entity
@Table(name = "staff")
@Immutable
public class Staff {

    @Id
    private Integer id;

    @Column(name = "full_name")
    private String fullName;

    protected Staff() {}

    public Integer getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Staff other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
