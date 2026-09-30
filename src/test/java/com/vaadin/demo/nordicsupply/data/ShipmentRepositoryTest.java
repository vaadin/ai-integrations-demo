package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The shipment queries against the dataset as generated. */
@SpringBootTest
class ShipmentRepositoryTest {

    @Autowired
    private ShipmentRepository shipments;

    @Test
    void anOrdersShipmentsAreItsOwnInTheOrderTheyLeft() {
        assertThat(shipments.optionsOf(2))
                .extracting(ShipmentRepository.Option::shipmentNumber)
                .containsExactly("SH-2025-000002", "SH-2025-000003");
    }
}
