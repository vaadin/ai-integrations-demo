package com.vaadin.demo.nordicsupply.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.Customer;

/** Customers: the list view and the customers tile. */
public interface CustomerRepository extends JpaRepository<Customer, Integer> {

    long countByActiveTrue();

    /** Rows of the Customers list. */
    record Row(
            String customerNumber,
            String name,
            String chainName,
            String segment,
            String city,
            String country,
            Integer paymentTermsDays,
            boolean active) {}

    /** The rows matching a {@link SearchTerms#like} term on name, number or city; every row for {@code ""}. */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.CustomerRepository$Row(
                c.customerNumber, c.name, c.chainName, c.segment, c.city, c.country, c.paymentTermsDays, c.active)
            from Customer c
            where :term = '' or lower(c.name) like :term or lower(c.customerNumber) like :term
               or lower(c.city) like :term
            """)
    Page<Row> search(@Param("term") String term, Pageable pageable);
}
