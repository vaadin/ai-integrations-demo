package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vaadin.demo.nordicsupply.domain.ClaimStatus;

/** The claim queries against the dataset as generated. */
@SpringBootTest
class ClaimRepositoryTest {

    @Autowired
    private ClaimRepository claims;

    @Test
    void countsTheClaimsThatAreNotResolvedClosedOrRejected() {
        long open = claims.findAll().stream()
                .filter(claim -> claim.getStatus() != ClaimStatus.RESOLVED
                        && claim.getStatus() != ClaimStatus.CLOSED
                        && claim.getStatus() != ClaimStatus.REJECTED)
                .count();

        assertThat(claims.countOpen()).isEqualTo(67).isEqualTo(open);
    }
}
