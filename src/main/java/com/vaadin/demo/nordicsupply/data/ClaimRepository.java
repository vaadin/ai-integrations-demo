package com.vaadin.demo.nordicsupply.data;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.demo.nordicsupply.domain.Claim;
import com.vaadin.demo.nordicsupply.domain.ClaimStatus;

/** Claims: the open-claims tile. */
public interface ClaimRepository extends JpaRepository<Claim, Integer> {

    /** The statuses a claim leaves the desk's queue with. */
    List<ClaimStatus> SETTLED = List.of(ClaimStatus.RESOLVED, ClaimStatus.CLOSED, ClaimStatus.REJECTED);

    long countByStatusNotIn(Collection<ClaimStatus> statuses);

    /** Claims that are neither resolved, closed nor rejected. */
    default long countOpen() {
        return countByStatusNotIn(SETTLED);
    }
}
