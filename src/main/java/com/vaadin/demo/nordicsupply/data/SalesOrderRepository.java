package com.vaadin.demo.nordicsupply.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /** The rows matching a {@link SearchTerms#like} term on number, customer or status; every row for {@code ""}. */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.SalesOrderRepository$Row(
                o.orderNumber, c.name, o.placedAt, o.status, o.promisedDeliveryDate, o.channel, o.totalNet)
            from SalesOrder o join o.customer c
            where :term = '' or lower(o.orderNumber) like :term escape '\\' or lower(c.name) like :term escape '\\'
               or lower(o.status) like :term escape '\\'
            """)
    Page<Row> search(@Param("term") String term, Pageable pageable);
}
