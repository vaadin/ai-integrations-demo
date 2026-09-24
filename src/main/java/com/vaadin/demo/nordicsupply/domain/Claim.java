package com.vaadin.demo.nordicsupply.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A customer complaint about an order or a shipment.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private Integer id;

    @Column(name = "claim_number")
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type")
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private ClaimSource source;

    @Column(name = "reported_by_contact_id")
    private Integer reportedByContactId;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_resolution")
    private RequestedResolution requestedResolution;

    @Column(name = "claimed_amount")
    private BigDecimal claimedAmount;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private SalesOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    protected Claim() {}

    public Integer getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public ClaimSource getSource() {
        return source;
    }

    public Integer getReportedByContactId() {
        return reportedByContactId;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDate getNeededBy() {
        return neededBy;
    }

    public RequestedResolution getRequestedResolution() {
        return requestedResolution;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public String getDescription() {
        return description;
    }

    public Customer getCustomer() {
        return customer;
    }

    public SalesOrder getOrder() {
        return order;
    }

    public Shipment getShipment() {
        return shipment;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Claim other && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
