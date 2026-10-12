package com.vaadin.demo.nordicsupply.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.vaadin.demo.nordicsupply.data.ClaimRepository;
import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.ProductRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.SavedWidgetRepository;
import com.vaadin.demo.nordicsupply.data.ScopeAssignmentRepository;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.data.StaffRepository;

/**
 * Every entity maps a table or view that the dataset or a set-up script creates. Hibernate is told never to touch
 * the schema, so nothing validates the mappings at start-up; this test does, by reading a row of every entity the
 * dataset fills, which selects every column, and by checking that each association reaches the row the foreign key
 * names. The activity log starts empty; its own tests write and read every column it maps.
 */
@SpringBootTest
class EntityMappingTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerContactRepository contacts;

    @Autowired
    private SalesOrderRepository orders;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private ProductRepository products;

    @Autowired
    private StaffRepository staff;

    @Autowired
    private ScopeAssignmentRepository assignments;

    @Autowired
    private SavedWidgetRepository savedWidgets;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void everyAggregateReadsOneRow() {
        Map<String, PagingAndSortingRepository<?, ?>> aggregates = new LinkedHashMap<>();
        aggregates.put("Customer", customers);
        aggregates.put("CustomerContact", contacts);
        aggregates.put("SalesOrder", orders);
        aggregates.put("Shipment", shipments);
        aggregates.put("Claim", claims);
        aggregates.put("Product", products);
        aggregates.put("Staff", staff);
        aggregates.put("ScopeAssignment", assignments);
        aggregates.put("SavedWidget", savedWidgets);
        aggregates.forEach((name, repository) -> assertThat(
                        repository.findAll(PageRequest.of(0, 1)).getContent())
                .as(name)
                .hasSize(1));
    }

    /** The lookup tables and the current-price view have no repository; their mappings are read through JPQL. */
    @Test
    void everyLookupReadsOneRow() {
        for (var entity : List.of("Category", "Supplier", "ProductCurrentPrice")) {
            assertThat(entityManager
                            .createQuery("select e from " + entity + " e")
                            .setMaxResults(1)
                            .getResultList())
                    .as(entity)
                    .hasSize(1);
        }
    }

    /** Rows whose keys differ from each other, so that two join columns swapped would not go unnoticed. */
    @Test
    void everyAssociationReachesTheRowItsForeignKeyNames() {
        int claimId = firstId(
                "claims",
                "order_id is not null and shipment_id is not null"
                        + " and customer_id <> order_id and order_id <> shipment_id and customer_id <> shipment_id");
        int orderId = firstId("orders", "customer_id is not null");
        int shipmentId = firstId("shipments", "order_id is not null");
        int productId = firstId("products", "category_id <> supplier_id");
        int contactId = firstId("customer_contacts", "customer_id <> id");
        var claim = claims.findById(claimId).orElseThrow();
        var order = orders.findById(orderId).orElseThrow();
        var shipment = shipments.findById(shipmentId).orElseThrow();
        var product = products.findById(productId).orElseThrow();
        var contact = contacts.findById(contactId).orElseThrow();

        assertThat(claim.getCustomer().getId()).isEqualTo(column("claims", "customer_id", claimId));
        assertThat(claim.getOrder().getId()).isEqualTo(column("claims", "order_id", claimId));
        assertThat(claim.getShipment().getId()).isEqualTo(column("claims", "shipment_id", claimId));
        assertThat(order.getCustomer().getId()).isEqualTo(column("orders", "customer_id", orderId));
        assertThat(shipment.getOrder().getId()).isEqualTo(column("shipments", "order_id", shipmentId));
        assertThat(product.getCategory().getId()).isEqualTo(column("products", "category_id", productId));
        assertThat(product.getSupplier().getId()).isEqualTo(column("products", "supplier_id", productId));
        assertThat(contact.getCustomer().getId()).isEqualTo(column("customer_contacts", "customer_id", contactId));
    }

    /** A saved widget's chat is the turns that name it, in their order, and every turn reaches its widget. */
    @Test
    void aSavedWidgetsChatIsTheTurnsThatNameIt() {
        for (var widget : savedWidgets.findByUserIdOrderByPositionAscIdAsc(9)) {
            assertThat(widget.getMessages())
                    .extracting(SavedWidgetMessage::getId)
                    .containsExactlyElementsOf(jdbc.queryForList(
                            "select id from saved_widget_messages where widget_id = ? order by position",
                            Long.class,
                            widget.getId()));
            assertThat(widget.getMessages()).allSatisfy(turn -> {
                assertThat(turn.getWidget().getId()).isEqualTo(widget.getId());
                assertThat(turn.getRole()).isNotNull();
                assertThat(turn.getContent()).isNotBlank();
                assertThat(turn.getSentAt()).isNotNull();
            });
        }
    }

    /**
     * A reference taken inside a session is a proxy of the entity; compared with the same row loaded earlier, the two
     * are equal both ways and hash alike, for every entity type. Calling {@code equals} on the proxy initialises it,
     * which needs the session the transaction provides; the loaded entity comes from outside it so that the two are
     * distinct objects.
     */
    @Test
    void aProxyEqualsTheEntityItStandsFor() {
        for (var type : List.of(
                Customer.class,
                CustomerContact.class,
                Product.class,
                Category.class,
                Supplier.class,
                ProductCurrentPrice.class,
                SalesOrder.class,
                Shipment.class,
                Claim.class,
                Staff.class,
                ScopeAssignment.class,
                SavedWidget.class)) {
            var loaded = entityManager.find(type, 1);

            transactions.executeWithoutResult(status -> {
                var proxy = entityManager.getReference(type, 1);

                assertThat(proxy).as(type.getSimpleName()).isNotSameAs(loaded).isEqualTo(loaded);
                assertThat(loaded).as(type.getSimpleName()).isEqualTo(proxy);
                assertThat(proxy.hashCode()).as(type.getSimpleName()).isEqualTo(loaded.hashCode());
            });
        }
    }

    /** The lowest id of a row matching the condition, read outside JPA. */
    private int firstId(String table, String condition) {
        return jdbc.queryForObject("select min(id) from " + table + " where " + condition, Integer.class);
    }

    /** A foreign-key column of one row, read outside JPA. */
    private Integer column(String table, String column, int id) {
        return jdbc.queryForObject("select " + column + " from " + table + " where id = " + id, Integer.class);
    }
}
