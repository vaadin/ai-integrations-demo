package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.signals.local.ValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.config.DisplayLocale;
import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.domain.ClaimType;
import com.vaadin.demo.nordicsupply.domain.Priority;
import com.vaadin.demo.nordicsupply.domain.RequestedResolution;

/**
 * The form offers each choice what the one before it leads to, and waits with a choice until there is something to
 * choose from; Create waits for a customer and what the claim is about, for valid values and for the AI to finish,
 * and a created claim empties the form.
 */
class ClaimFormTest {

    private static final CustomerRepository.Option AALBORG = new CustomerRepository.Option(
            1186, "C-11186", "Aalborg Trekking ApS", "Aalborg", "aalborg.trekking.example");
    private static final CustomerRepository.Option TARTU =
            new CustomerRepository.Option(1020, "C-12020", "Tartu Urheilu OÜ", "Tartu", "tartu.urheilu.example");
    private static final CustomerContactRepository.Option NIELS = new CustomerContactRepository.Option(
            2133, "Niels Madsen", "Store Manager", "niels.madsen@aalborg.trekking.example");
    private static final SalesOrderRepository.Option ORDER = new SalesOrderRepository.Option(
            6216,
            "SO-2026-006216",
            LocalDateTime.of(2026, 8, 26, 13, 17),
            "PO-84764",
            LocalDate.of(2026, 8, 31),
            LocalDateTime.of(2026, 8, 31, 15, 15),
            new BigDecimal("9525.07"));
    private static final ShipmentRepository.Option SHIPMENT = new ShipmentRepository.Option(
            11808,
            "SH-2026-006654",
            "Havnelast",
            "HA8320332807",
            LocalDateTime.of(2026, 8, 27, 12, 58),
            LocalDateTime.of(2026, 8, 31, 15, 15),
            1);

    private static final ShipmentRepository.Option LATER_SHIPMENT = new ShipmentRepository.Option(
            11809,
            "SH-2026-006655",
            "Havnelast",
            "HA8320332808",
            LocalDateTime.of(2026, 9, 2, 9, 30),
            LocalDateTime.of(2026, 9, 3, 11, 5),
            0);

    /** Held here, since the current UI is kept only weakly and a garbage collection would clear it mid-test. */
    private final UI ui = new UI();

    private ShipmentRepository shipments;
    private final ValueSignal<Boolean> filling = new ValueSignal<>(false);
    private ClaimForm form;
    private ComboBox<CustomerRepository.Option> customer;
    private ComboBox<CustomerContactRepository.Option> reportedBy;
    private ComboBox<SalesOrderRepository.Option> order;
    private ComboBox<ShipmentRepository.Option> shipment;
    private Select<ClaimType> claimType;
    private RadioButtonGroup<Priority> priority;
    private DatePicker neededBy;
    private BigDecimalField claimedAmount;
    private TextArea description;
    private Button create;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void aFormForTheRegionOfAalborg() {
        ui.setLocale(DisplayLocale.LOCALE);
        UI.setCurrent(ui);
        var contacts = mock(CustomerContactRepository.class);
        var orders = mock(SalesOrderRepository.class);
        shipments = mock(ShipmentRepository.class);
        when(contacts.optionsOf(AALBORG.id())).thenReturn(List.of(NIELS));
        when(orders.optionsOf(AALBORG.id())).thenReturn(List.of(ORDER));
        when(shipments.optionsOf(ORDER.id())).thenReturn(List.of(SHIPMENT));
        form = new ClaimForm(mock(CustomerRepository.class), contacts, orders, shipments, List.of("DK", "DE"), filling);
        ui.add(form);

        customer = field("customer", ComboBox.class);
        reportedBy = field("reported-by", ComboBox.class);
        order = field("order", ComboBox.class);
        shipment = field("shipment", ComboBox.class);
        claimType = field("claim-type", Select.class);
        priority = field("priority", RadioButtonGroup.class);
        neededBy = field("needed-by", DatePicker.class);
        claimedAmount = field("claimed-amount", BigDecimalField.class);
        description = field("description", TextArea.class);
        create = ComponentTree.descendants(form)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Create Claim".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void theContactAndTheOrderWaitForTheCustomer() {
        assertThat(reportedBy.isEnabled()).isFalse();
        assertThat(order.isEnabled()).isFalse();
        assertThat(shipment.isEnabled()).isFalse();
    }

    @Test
    void theContactsAndOrdersToChooseFromAreTheCustomers() {
        customer.setValue(AALBORG);

        assertThat(reportedBy.isEnabled()).isTrue();
        assertThat(reportedBy.getListDataView().getItems()).containsExactly(NIELS);
        assertThat(order.isEnabled()).isTrue();
        assertThat(order.getListDataView().getItems()).containsExactly(ORDER);
        assertThat(shipment.isEnabled()).isFalse();
    }

    @Test
    void anOrderThatCameInOneShipmentChoosesIt() {
        customer.setValue(AALBORG);
        order.setValue(ORDER);

        assertThat(shipment.isEnabled()).isTrue();
        assertThat(shipment.getListDataView().getItems()).containsExactly(SHIPMENT);
        assertThat(shipment.getValue()).isEqualTo(SHIPMENT);
    }

    @Test
    void anOrderThatCameInSeveralShipmentsLeavesTheChoiceOpen() {
        when(shipments.optionsOf(ORDER.id())).thenReturn(List.of(SHIPMENT, LATER_SHIPMENT));
        customer.setValue(AALBORG);
        order.setValue(ORDER);

        assertThat(shipment.getListDataView().getItems()).containsExactly(SHIPMENT, LATER_SHIPMENT);
        assertThat(shipment.getValue()).isNull();
    }

    @Test
    void anotherCustomerEmptiesTheContactTheOrderAndTheShipment() {
        customer.setValue(AALBORG);
        reportedBy.setValue(NIELS);
        order.setValue(ORDER);
        shipment.setValue(SHIPMENT);

        customer.setValue(TARTU);

        assertThat(reportedBy.getValue()).isNull();
        assertThat(order.getValue()).isNull();
        assertThat(shipment.getValue()).isNull();
        assertThat(shipment.isEnabled()).isFalse();
    }

    @Test
    void createWaitsForACustomerAndWhatTheClaimIsAbout() {
        assertThat(create.isEnabled()).isFalse();

        description.setValue("Two cartons missing.");
        customer.setValue(AALBORG);
        assertThat(create.isEnabled()).isFalse();

        claimType.setValue(ClaimType.MISSING_ITEMS);
        assertThat(create.isEnabled()).isTrue();
        assertThat(((HasValidation) customer).isInvalid()).isFalse();
    }

    @Test
    void createWaitsForAValidAmount() {
        customer.setValue(AALBORG);
        claimType.setValue(ClaimType.MISSING_ITEMS);

        claimedAmount.setValue(new BigDecimal("-1"));

        assertThat(create.isEnabled()).isFalse();
    }

    /** A claim is made on the dataset's day, the demo's today, so the customer cannot need it sooner. */
    @Test
    void createWaitsForANeededByDateFromTheDatasetsDayOn() {
        customer.setValue(AALBORG);
        claimType.setValue(ClaimType.MISSING_ITEMS);

        neededBy.setValue(LocalDate.of(2026, 9, 6));
        assertThat(create.isEnabled()).isFalse();
        assertThat(neededBy.getErrorMessage()).isEqualTo("The date cannot be in the past");

        neededBy.setValue(LocalDate.of(2026, 9, 7));
        assertThat(create.isEnabled()).isTrue();
    }

    @Test
    void createWaitsForTheAiToFinish() {
        customer.setValue(AALBORG);
        claimType.setValue(ClaimType.MISSING_ITEMS);

        filling.set(true);
        assertThat(create.isEnabled()).isFalse();

        filling.set(false);
        assertThat(create.isEnabled()).isTrue();
    }

    @Test
    void anEmptiedFormWaitsAgain() {
        customer.setValue(AALBORG);
        claimType.setValue(ClaimType.MISSING_ITEMS);

        form.clear();

        assertThat(create.isEnabled()).isFalse();
        assertThat(form.filledByAi().peek()).isFalse();
    }

    @Test
    void aCreatedClaimEmptiesTheForm() {
        customer.setValue(AALBORG);
        claimType.setValue(ClaimType.MISSING_ITEMS);
        priority.setValue(Priority.HIGH);
        description.setValue("Two cartons missing.");

        create.click();

        assertThat(customer.getValue()).isNull();
        assertThat(claimType.getValue()).isNull();
        assertThat(priority.getValue()).isEqualTo(Priority.NORMAL);
        assertThat(description.getValue()).isEmpty();
    }

    @Test
    void theConfirmationSaysWhoseClaimItIsWhatAboutHowUrgentAndForHowMuch() {
        var draft = new ClaimForm.Draft();
        draft.setCustomer(AALBORG);
        draft.setClaimType(ClaimType.MISSING_ITEMS);
        draft.setPriority(Priority.HIGH);
        draft.setClaimedAmount(new BigDecimal("1161.86"));

        assertThat(ClaimForm.confirmation(draft, DisplayLocale.LOCALE))
                .isEqualTo("Claim created for Aalborg Trekking ApS: Missing items, high priority, 1\u00a0161,86\u00a0€."
                        + " The demo does not save claims, so the form starts over.");
    }

    @Test
    void anEnumIsWrittenAsWords() {
        assertThat(ClaimForm.label(RequestedResolution.RETURN_AUTHORISATION)).isEqualTo("Return authorisation");
        assertThat(ClaimForm.label(Priority.LOW)).isEqualTo("Low");
        assertThat(ClaimForm.label(null)).isEmpty();
    }

    private <T> T field(String id, Class<T> type) {
        return ComponentTree.descendants(form)
                .filter(component -> component.getId().filter(id::equals).isPresent())
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}
