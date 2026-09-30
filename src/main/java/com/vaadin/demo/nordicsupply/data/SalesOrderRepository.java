package com.vaadin.demo.nordicsupply.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.SalesOrder;

/** Sales orders: the list view. */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Integer> {

    /** Rows of the Orders list. */
    record Row(
            String orderNumber,
            String customer,
            LocalDateTime placedAt,
            String status,
            LocalDate promisedDeliveryDate,
            String channel,
            BigDecimal totalNet) {}

    /**
     * The rows of the orders of customers in the given countries matching a {@link SearchTerms#like} term on number,
     * customer or status; every such row for {@code ""}.
     */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.SalesOrderRepository$Row(
                o.orderNumber, c.name, o.placedAt, o.status, o.promisedDeliveryDate, o.channel, o.totalNet)
            from SalesOrder o join o.customer c
            where c.country in :countries
              and (:term = '' or lower(o.orderNumber) like :term escape '\\' or lower(c.name) like :term escape '\\'
                   or lower(o.status) like :term escape '\\')
            """)
    Page<Row> search(@Param("term") String term, @Param("countries") List<String> countries, Pageable pageable);

    /**
     * An order to choose from: its number, and what a message may name it by instead: when it was placed, the customer's
     * own reference, when it was promised and last delivered, and its net value.
     */
    record Option(
            Integer id,
            String orderNumber,
            LocalDateTime placedAt,
            String customerReference,
            LocalDate promisedDeliveryDate,
            LocalDateTime lastDeliveredAt,
            BigDecimal totalNet) {}

    /** The orders of one customer, newest first; the last delivery is {@code null} while none has arrived. */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.SalesOrderRepository$Option(
                o.id, o.orderNumber, o.placedAt, o.customerReference, o.promisedDeliveryDate,
                (select max(s.deliveredAt) from Shipment s where s.order = o), o.totalNet)
            from SalesOrder o
            where o.customer.id = :customerId
            order by o.placedAt desc, o.id desc
            """)
    List<Option> optionsOf(@Param("customerId") int customerId);
}
