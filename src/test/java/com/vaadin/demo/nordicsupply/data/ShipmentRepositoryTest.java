package com.vaadin.demo.nordicsupply.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The shipment queries against the dataset as generated. */
@SpringBootTest
class ShipmentRepositoryTest {

    @Autowired
    private ShipmentRepository shipments;

    @Test
    void countsTheDispatchesThatLeftAfterThePromisedShipDate() {
        var august = LocalDate.of(2026, 8, 1).atStartOfDay();
        var september = LocalDate.of(2026, 9, 1).atStartOfDay();

        assertThat(shipments.countLateDispatches(august, september)).isEqualTo(361);
    }

    @Test
    void anEmptyPeriodHasNoLateDispatches() {
        var day = LocalDate.of(2026, 8, 1).atStartOfDay();

        assertThat(shipments.countLateDispatches(day, day)).isZero();
    }
}
