package com.vaadin.demo.nordicsupply.data;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.Shipment;

/** Shipments of the orders. */
public interface ShipmentRepository extends JpaRepository<Shipment, Integer> {

    /**
     * A shipment to choose from: its number, and the carrier, tracking number, dates and pallets a message may name it
     * by.
     */
    record Option(
            Integer id,
            String shipmentNumber,
            String carrier,
            String trackingNumber,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            int palletCount) {}

    /** The shipments of one order, in the order they left. */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.ShipmentRepository$Option(
                s.id, s.shipmentNumber, s.carrier, s.trackingNumber, s.shippedAt, s.deliveredAt, s.palletCount)
            from Shipment s
            where s.order.id = :orderId
            order by s.shippedAt, s.id
            """)
    List<Option> optionsOf(@Param("orderId") int orderId);
}
