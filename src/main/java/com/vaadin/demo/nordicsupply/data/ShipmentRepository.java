package com.vaadin.demo.nordicsupply.data;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.Shipment;

/** Shipments: the late-dispatches tile. */
public interface ShipmentRepository extends JpaRepository<Shipment, Integer> {

    /** Shipments dispatched after their order's promised ship date, among those shipped within [from, to). */
    @Query("""
            select count(s) from Shipment s join s.order o
            where s.shippedAt >= :from and s.shippedAt < :to
              and cast(s.shippedAt as LocalDate) > o.promisedShipDate
            """)
    long countLateDispatches(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
