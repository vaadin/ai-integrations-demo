package com.vaadin.demo.nordicsupply.data;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.Customer;

/** Customers: the list view. */
public interface CustomerRepository extends JpaRepository<Customer, Integer> {

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

    /**
     * The rows of the customers in the given countries matching a {@link SearchTerms#like} term on name, number or
     * city; every row of those countries for {@code ""}.
     */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.CustomerRepository$Row(
                c.customerNumber, c.name, c.chainName, c.segment, c.city, c.country, c.paymentTermsDays, c.active)
            from Customer c
            where c.country in :countries
              and (:term = '' or lower(c.name) like :term escape '\\' or lower(c.customerNumber) like :term escape '\\'
                   or lower(c.city) like :term escape '\\')
            """)
    Page<Row> search(@Param("term") String term, @Param("countries") List<String> countries, Pageable pageable);
}
