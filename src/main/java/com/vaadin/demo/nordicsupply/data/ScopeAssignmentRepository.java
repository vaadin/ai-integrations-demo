package com.vaadin.demo.nordicsupply.data;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.ScopeAssignment;

/** Which employees the demo can sign in as, and the region of each. */
public interface ScopeAssignmentRepository extends JpaRepository<ScopeAssignment, Integer> {

    /** Every assignment, in the order of the employees' ids. */
    List<ScopeAssignment> findAllByOrderByUserId();
}
