package com.vaadin.demo.nordicsupply.data;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.Staff;

/** The employees, as the user switcher needs them. */
public interface StaffRepository extends JpaRepository<Staff, Integer> {}
