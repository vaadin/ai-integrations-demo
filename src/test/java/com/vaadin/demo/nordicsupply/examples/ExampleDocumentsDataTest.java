package com.vaadin.demo.nordicsupply.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The example documents name customers, contacts, orders, shipments and products of the dataset, so that the assistant
 * finds what it reads about. The documents are written by hand and the dataset is generated, so this test keeps the
 * two together: every name, number, date and price a document states is the dataset's.
 */
@SpringBootTest
class ExampleDocumentsDataTest {

    /** An order line as a document states it: the product, how many, and the unit price when the document gives it. */
    private record Line(String sku, int quantity, String unitPrice) {}

    /** A shipment as a document states it; a detail the document leaves out is {@code null}. */
    private record Shipment(String number, String carrier, String tracking, LocalDate delivered) {}

    /** What one document says about the dataset. */
    private record Facts(
            String document,
            String customer,
            String city,
            String contactEmail,
            String order,
            String customerReference,
            String totalNet,
            Shipment shipment,
            List<Line> lines) {}

    private static final List<Facts> FACTS = List.of(
            new Facts(
                    "Damaged pallet.pdf",
                    "Ridge Sport & Fritid Oy",
                    "Rovaniemi",
                    "pekka.koskinen@ridge.sport.fritid.example",
                    "SO-2026-006830",
                    "PO-24154",
                    null,
                    new Shipment("SH-2026-007291", "Kalottfrakt", "KA9456355659", LocalDate.of(2026, 8, 28)),
                    List.of(
                            new Line("NVD-SHL-0026", 96, "197.90"),
                            new Line("KAJ-WTR-0013", 24, null),
                            new Line("ORE-CYC-0019", 12, null),
                            new Line("TAI-BAS-0017", 12, null))),
            new Facts(
                    "Missing cartons.png",
                    "Forestshop AB",
                    "Uppsala",
                    "astrid.larsson@forestshop.example",
                    "SO-2026-006314",
                    null,
                    null,
                    new Shipment("SH-2026-006754", "Kalott Freight", "KA1220942516", LocalDate.of(2026, 9, 2)),
                    List.of(new Line("SIS-SHL-0045", 24, "152.90"), new Line("FJV-PCK-0030", 1, null))),
            new Facts(
                    "Late delivery.pdf",
                    "Berlin Fritid GmbH",
                    "Berlin",
                    "katharina.wagner@berlin.fritid.example",
                    "SO-2026-006433",
                    null,
                    "3592.68",
                    new Shipment("SH-2026-006877", "Kalott Freight", "KA1151121507", LocalDate.of(2026, 8, 24)),
                    List.of(new Line("PEH-FTW-0037", 12, null), new Line("GRA-FTW-0040", 8, null))),
            new Facts(
                    "Quality defect.jpg",
                    "Polar Adventure AS",
                    "Oslo",
                    "eirik.bakke@polar.adventure.example",
                    "SO-2026-006423",
                    null,
                    null,
                    new Shipment("SH-2026-006866", null, null, null),
                    List.of(new Line("ALV-KIT-0020", 30, "55.00"))),
            new Facts(
                    "Pricing dispute.pdf",
                    "Sørensen Outdoor ApS",
                    "Roskilde",
                    "mads.rasmussen@sorensen.outdoor.example",
                    "SO-2026-006912",
                    "PO-49898",
                    "29883.33",
                    new Shipment("SH-2026-007377", null, null, LocalDate.of(2026, 8, 24)),
                    List.of(
                            new Line("RON-SLP-0021", 48, "390.50"),
                            new Line("KOM-NAV-0025", 2, "13.82"),
                            new Line("MYR-SHL-0007", 30, "418.90"),
                            new Line("HAL-ACC-0035", 1, "117.50"))),
            new Facts(
                    "Wrong colour.png",
                    "Aapastore OÜ",
                    "Tartu",
                    "kadri.rebane@aapastore.example",
                    "SO-2026-006473",
                    null,
                    null,
                    new Shipment("SH-2026-006922", null, null, null),
                    List.of(new Line("NVD-SHL-0043", 36, "193.90"))));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyDocumentIsOffered() {
        assertThat(PaintedExamples.ALL.all())
                .extracting(ExampleDocument::name)
                .containsExactlyInAnyOrderElementsOf(
                        FACTS.stream().map(Facts::document).toList());
    }

    @Test
    void theCustomersAndTheirContactsExist() {
        for (var facts : FACTS) {
            var customer = jdbc.queryForMap("select id, city from customers where name = ?", facts.customer());
            assertThat(customer.get("CITY")).as(facts.document()).isEqualTo(facts.city());
            assertThat(jdbc.queryForObject(
                            "select customer_id from customer_contacts where email = ?",
                            Integer.class,
                            facts.contactEmail()))
                    .as("%s: the contact's customer", facts.document())
                    .isEqualTo(customer.get("ID"));
        }
    }

    @Test
    void theOrdersAreTheCustomersAndSayWhatTheDocumentsSay() {
        for (var facts : FACTS) {
            var order = jdbc.queryForMap(
                    "select o.id, c.name as customer, o.customer_reference, o.total_net from orders o"
                            + " join customers c on c.id = o.customer_id where o.order_number = ?",
                    facts.order());
            assertThat(order.get("CUSTOMER")).as(facts.document()).isEqualTo(facts.customer());
            if (facts.customerReference() != null) {
                assertThat(order.get("CUSTOMER_REFERENCE")).as(facts.document()).isEqualTo(facts.customerReference());
            }
            if (facts.totalNet() != null) {
                assertThat((BigDecimal) order.get("TOTAL_NET"))
                        .as(facts.document())
                        .isEqualByComparingTo(facts.totalNet());
            }
        }
    }

    @Test
    void theShipmentsBelongToTheOrdersAndSayWhatTheDocumentsSay() {
        for (var facts : FACTS) {
            var shipment = jdbc.queryForMap(
                    "select o.order_number, s.carrier, s.tracking_number, cast(s.delivered_at as date) as delivered"
                            + " from shipments s join orders o on o.id = s.order_id where s.shipment_number = ?",
                    facts.shipment().number());
            assertThat(shipment.get("ORDER_NUMBER")).as(facts.document()).isEqualTo(facts.order());
            if (facts.shipment().carrier() != null) {
                assertThat(shipment.get("CARRIER"))
                        .as(facts.document())
                        .isEqualTo(facts.shipment().carrier());
            }
            if (facts.shipment().tracking() != null) {
                assertThat(shipment.get("TRACKING_NUMBER"))
                        .as(facts.document())
                        .isEqualTo(facts.shipment().tracking());
            }
            if (facts.shipment().delivered() != null) {
                assertThat(((Date) shipment.get("DELIVERED")).toLocalDate())
                        .as(facts.document())
                        .isEqualTo(facts.shipment().delivered());
            }
        }
    }

    @Test
    void theOrderLinesAreTheProductsQuantitiesAndPricesTheDocumentsName() {
        for (var facts : FACTS) {
            var lines = jdbc.queryForList(
                    "select p.sku, l.quantity, l.unit_price from order_lines l join products p on p.id = l.product_id"
                            + " join orders o on o.id = l.order_id where o.order_number = ?",
                    facts.order());
            var bySku = lines.stream().collect(Collectors.toMap(row -> row.get("SKU"), row -> row));
            for (var line : facts.lines()) {
                Map<String, Object> found = bySku.get(line.sku());
                assertThat(found)
                        .as("%s: line %s", facts.document(), line.sku())
                        .isNotNull();
                assertThat(((Number) found.get("QUANTITY")).intValue())
                        .as("%s: quantity of %s", facts.document(), line.sku())
                        .isEqualTo(line.quantity());
                if (line.unitPrice() != null) {
                    assertThat((BigDecimal) found.get("UNIT_PRICE"))
                            .as("%s: price of %s", facts.document(), line.sku())
                            .isEqualByComparingTo(line.unitPrice());
                }
            }
        }
    }
}
