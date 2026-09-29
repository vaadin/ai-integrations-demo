package com.vaadin.demo.nordicsupply.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Which region an employee is responsible for: one row per employee the demo can sign in as, written by the set-up
 * script and only read by the application. An application with real accounts replaces this table with the roles
 * of the authenticated user.
 */
@Entity
@Table(name = "scope_assignment")
@Immutable
public class ScopeAssignment {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "role_scope")
    private String roleScope;

    protected ScopeAssignment() {}

    public Integer getUserId() {
        return userId;
    }

    /** The key of the region, as {@code Scopes} names it. */
    public String getRoleScope() {
        return roleScope;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScopeAssignment other && Objects.equals(userId, other.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
