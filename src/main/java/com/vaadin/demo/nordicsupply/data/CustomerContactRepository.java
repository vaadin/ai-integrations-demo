package com.vaadin.demo.nordicsupply.data;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vaadin.demo.nordicsupply.domain.CustomerContact;

/** The people at the customers: who can report a claim. */
public interface CustomerContactRepository extends JpaRepository<CustomerContact, Integer> {

    /** A contact to choose from: the name to show, and the role and address that tell a message's sender apart. */
    record Option(Integer id, String name, String role, String email) {}

    /** The contacts of one customer, the primary one first, then by name. */
    @Query("""
            select new com.vaadin.demo.nordicsupply.data.CustomerContactRepository$Option(
                p.id, concat(p.firstName, ' ', p.lastName), p.role, p.email)
            from CustomerContact p
            where p.customer.id = :customerId
            order by p.primary desc, p.lastName, p.firstName, p.id
            """)
    List<Option> optionsOf(@Param("customerId") int customerId);
}
