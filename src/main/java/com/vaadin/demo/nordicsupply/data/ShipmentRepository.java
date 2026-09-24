package com.vaadin.demo.nordicsupply.data;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.Shipment;

/** Shipments of the orders. */
public interface ShipmentRepository extends JpaRepository<Shipment, Integer> {}
