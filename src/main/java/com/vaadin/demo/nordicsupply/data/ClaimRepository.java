package com.vaadin.demo.nordicsupply.data;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.Claim;

/** Customer claims. */
public interface ClaimRepository extends JpaRepository<Claim, Integer> {}
