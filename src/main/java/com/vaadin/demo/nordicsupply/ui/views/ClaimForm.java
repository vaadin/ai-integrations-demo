package com.vaadin.demo.nordicsupply.ui.views;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.ai.common.ValueSource;
import com.vaadin.flow.component.ai.form.FormAIController;
import com.vaadin.flow.component.ai.form.ValueOptions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.FormRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.shared.Registration;
import org.springframework.data.domain.PageRequest;

import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.SearchTerms;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.domain.ClaimType;
import com.vaadin.demo.nordicsupply.domain.Priority;
import com.vaadin.demo.nordicsupply.domain.RequestedResolution;

/**
 * The form a new claim is written in, by hand or by the AI from a customer's message: the customer and who reported
 * it, the order and the shipment, what the complaint is and how urgent, what the customer asks for, by when and for
 * how much, and a description. The contacts and orders to choose from are the chosen customer's, and the shipments
 * the chosen order's, so each of those fields waits, disabled, for the one before it; the AI is told the same and
 * fills them in that order. An order that came in one shipment chooses it. Create checks the form and confirms the
 * claim; the demo keeps no claims, so the form then starts over, and says so with a {@link CreatedEvent}.
 * <p>
 * The fields are bound to a {@link Draft} through a {@link Binder}, which both a person and the AI are validated
 * against, and {@link #aiController()} is the form's side of the AI: the options it may pick from, labelled for a
 * model that matches a message's sender, order and shipment to the data, a description of each field, and where in
 * the message the AI read each value. The form follows the field in focus and tells, with a {@link SourceEvent},
 * where its value came from, so that the message can show it.
 */
class ClaimForm extends Div {

    /**
     * Where the value of the field in focus came from, as the AI reported it: fired when the focus moves or the
     * value changes. None when no field is in focus, or its value is not the AI's.
     */
    static class SourceEvent extends ComponentEvent<ClaimForm> {

        private final ValueSource valueSource;

        SourceEvent(ClaimForm form, ValueSource valueSource) {
            super(form, false);
            this.valueSource = valueSource;
        }

        Optional<ValueSource> getValueSource() {
            return Optional.ofNullable(valueSource);
        }
    }

    /** A claim was created, and the form starts over. */
    static class CreatedEvent extends ComponentEvent<ClaimForm> {

        CreatedEvent(ClaimForm form) {
            super(form, false);
        }
    }

    /** What the form holds: a claim before it is created. Bound by property name, which the AI reads too. */
    public static class Draft {

        private CustomerRepository.Option customer;
        private CustomerContactRepository.Option reportedBy;
        private SalesOrderRepository.Option order;
        private ShipmentRepository.Option shipment;
        private ClaimType claimType;
        private Priority priority = Priority.NORMAL;
        private RequestedResolution requestedResolution;
        private LocalDate neededBy;
        private BigDecimal claimedAmount;
        private String description = "";

        public CustomerRepository.Option getCustomer() {
            return customer;
        }

        public void setCustomer(CustomerRepository.Option customer) {
            this.customer = customer;
        }

        public CustomerContactRepository.Option getReportedBy() {
            return reportedBy;
        }

        public void setReportedBy(CustomerContactRepository.Option reportedBy) {
            this.reportedBy = reportedBy;
        }

        public SalesOrderRepository.Option getOrder() {
            return order;
        }

        public void setOrder(SalesOrderRepository.Option order) {
            this.order = order;
        }

        public ShipmentRepository.Option getShipment() {
            return shipment;
        }

        public void setShipment(ShipmentRepository.Option shipment) {
            this.shipment = shipment;
        }

        public ClaimType getClaimType() {
            return claimType;
        }

        public void setClaimType(ClaimType claimType) {
            this.claimType = claimType;
        }

        public Priority getPriority() {
            return priority;
        }

        public void setPriority(Priority priority) {
            this.priority = priority;
        }

        public RequestedResolution getRequestedResolution() {
            return requestedResolution;
        }

        public void setRequestedResolution(RequestedResolution requestedResolution) {
            this.requestedResolution = requestedResolution;
        }

        public LocalDate getNeededBy() {
            return neededBy;
        }

        public void setNeededBy(LocalDate neededBy) {
            this.neededBy = neededBy;
        }

        public BigDecimal getClaimedAmount() {
            return claimedAmount;
        }

        public void setClaimedAmount(BigDecimal claimedAmount) {
            this.claimedAmount = claimedAmount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /** The longest description the claims table holds. */
    static final int DESCRIPTION_LENGTH = 1000;

    private final ComboBox<CustomerRepository.Option> customer = new ComboBox<>("Customer");
    private final ComboBox<CustomerContactRepository.Option> reportedBy = new ComboBox<>("Reported By");
    private final ComboBox<SalesOrderRepository.Option> order = new ComboBox<>("Order");
    private final ComboBox<ShipmentRepository.Option> shipment = new ComboBox<>("Shipment");
    private final Select<ClaimType> claimType = new Select<>();
    private final RadioButtonGroup<Priority> priority = new RadioButtonGroup<>("Priority");
    private final Select<RequestedResolution> requestedResolution = new Select<>();
    private final DatePicker neededBy = new DatePicker("Needed By");
    private final BigDecimalField claimedAmount = new BigDecimalField("Claimed Amount");
    private final TextArea description = new TextArea("Description");

    private final FormLayout fields = new FormLayout();
    private final Binder<Draft> binder = new Binder<>(Draft.class);
    private final Locale locale = getLocale();

    private final transient CustomerRepository customers;
    private final transient CustomerContactRepository contacts;
    private final transient SalesOrderRepository orders;
    private final transient ShipmentRepository shipments;
    private final List<String> countries;

    /** The form's side of the AI, once {@link #aiController()} made it; the sources of the values are in it. */
    private FormAIController controller;

    /** The field in focus, whose value's source is shown, or {@code null}. */
    private HasValue<?, ?> focused;

    /**
     * @param countries the signed-in user's region: the customers a claim can be for
     */
    ClaimForm(
            CustomerRepository customers,
            CustomerContactRepository contacts,
            SalesOrderRepository orders,
            ShipmentRepository shipments,
            List<String> countries) {
        this.customers = customers;
        this.contacts = contacts;
        this.orders = orders;
        this.shipments = shipments;
        this.countries = List.copyOf(countries);
        addClassName("claim-form");

        configureFields();
        bindFields();
        cascade();
        followFocus();

        // six columns: two fields to a row, three in the row of the resolution, the description across
        fields.setAutoResponsive(true);
        fields.setExpandColumns(true);
        fields.setExpandFields(true);
        fields.setMaxColumns(6);
        fields.setColumnWidth("5rem");
        var parties = new FormRow();
        parties.add(customer, 3);
        parties.add(reportedBy, 3);
        var delivery = new FormRow();
        delivery.add(order, 3);
        delivery.add(shipment, 3);
        var complaint = new FormRow();
        complaint.add(claimType, 3);
        complaint.add(priority, 3);
        var request = new FormRow();
        request.add(requestedResolution, 2);
        request.add(neededBy, 2);
        request.add(claimedAmount, 2);
        var text = new FormRow();
        text.add(description, 6);
        fields.add(parties, delivery, complaint, request, text);

        var create = new Button("Create Claim", e -> create());
        create.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.LARGE);
        create.addClassName("create");
        create.setWidthFull();
        add(fields, create);
    }

    /**
     * The form's side of the AI, for one orchestrator: the options of each choice labelled the way a message names
     * them, and what each field means where its label does not say.
     */
    FormAIController aiController() {
        controller = new FormAIController(fields, binder);
        controller.setSourceTrackingEnabled(true);
        // the AI badge's popover quotes what the value was read from
        controller.setFieldMarkerPopoverContentProvider(change -> change.getFieldSource()
                .filter(source -> !source.extracts().isEmpty())
                .map(ClaimForm::quotes)
                .orElse(null));
        // a turn that changed the field in focus shows where the new value came from
        controller.addFieldValueChangeListener(e -> {
            if (e.getField() == focused) {
                showSource();
            }
        });
        controller
                .fieldValueOptions(ValueOptions.forField(customer)
                        .options((filter, limit) ->
                                customers.options(SearchTerms.like(filter), countries, PageRequest.of(0, limit)))
                        .itemLabelGenerator(ClaimForm::customerForTheModel))
                .fieldValueOptions(ValueOptions.forField(reportedBy)
                        .options((filter, limit) ->
                                matching(contactsOf(customer.getValue()), filter, limit, ClaimForm::contactForTheModel))
                        .itemLabelGenerator(ClaimForm::contactForTheModel))
                .fieldValueOptions(ValueOptions.forField(order)
                        .options((filter, limit) ->
                                matching(ordersOf(customer.getValue()), filter, limit, ClaimForm::orderForTheModel))
                        .itemLabelGenerator(ClaimForm::orderForTheModel))
                .fieldValueOptions(ValueOptions.forField(shipment)
                        .options((filter, limit) ->
                                matching(shipmentsOf(order.getValue()), filter, limit, ClaimForm::shipmentForTheModel))
                        .itemLabelGenerator(ClaimForm::shipmentForTheModel))
                .describeField(
                        customer,
                        "The retail customer the message comes from. Search by the sender's mail domain, the shop's"
                                + " name or city, or a customer number; each option shows the customer's number, city"
                                + " and mail domain.")
                .describeField(reportedBy, "The person at the customer who reported the problem, usually the sender.")
                .describeField(
                        order,
                        "The customer's order the claim is about: match the order number, or the customer's own"
                                + " reference for it (a PO number), or when it was placed.")
                .describeField(
                        shipment,
                        "The delivery of that order the problem came with: match the shipment number, the tracking"
                                + " number, the carrier or the delivery date.")
                .describeField(
                        priority,
                        "Urgent only when the customer's business is at a standstill until it is fixed; High when"
                                + " the resolution is needed within about a week, or for a season or event about to"
                                + " start; Low for a minor issue with no deadline; otherwise Normal.")
                .describeField(
                        neededBy, "The date the customer needs the resolution by, only when the message gives one.")
                .describeField(
                        claimedAmount,
                        "The amount claimed in euros, as a sum of money: the amount the message states, the value"
                                + " of the affected goods at the prices it quotes, or a share of the order it asks"
                                + " for, taken from the order's net value. Never estimate a price the message or the"
                                + " order does not give; leave the field empty instead.")
                .describeField(
                        description,
                        "A summary of the complaint for the claims team, in English whatever the language of the"
                                + " message: what is affected and how many, what happened, and what the customer asks"
                                + " for. Leave out personal details the claim does not need: no phone numbers, and no"
                                + " names but the reporter's. At most " + DESCRIPTION_LENGTH + " characters.");
        return controller;
    }

    /** Empties the form for the next claim; the priority starts at Normal. */
    void clear() {
        binder.readBean(new Draft());
    }

    private void configureFields() {
        customer.setId("customer");
        customer.setPlaceholder("Select Customer");
        customer.setItemLabelGenerator(CustomerRepository.Option::name);
        customer.setItemsPageable((page, filter) -> customers.options(SearchTerms.like(filter), countries, page));

        reportedBy.setId("reported-by");
        reportedBy.setItemLabelGenerator(CustomerContactRepository.Option::name);

        order.setId("order");
        order.setPlaceholder("Select Order");
        order.setItemLabelGenerator(SalesOrderRepository.Option::orderNumber);

        shipment.setId("shipment");
        shipment.setPlaceholder("Select Shipment");
        shipment.setItemLabelGenerator(ShipmentRepository.Option::shipmentNumber);

        claimType.setId("claim-type");
        claimType.setLabel("Claim Type");
        claimType.setPlaceholder("Select Claim Type");
        claimType.setItems(ClaimType.values());
        claimType.setItemLabelGenerator(ClaimForm::label);

        priority.setId("priority");
        // the group is an Aura surface, as fields are, for its segments' pill to take the fields' colour
        priority.addClassNames("segmented", "aura-surface");
        priority.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        priority.setItems(Priority.values());
        priority.setItemLabelGenerator(ClaimForm::label);

        requestedResolution.setId("requested-resolution");
        requestedResolution.setLabel("Requested Resolution");
        requestedResolution.setPlaceholder("Select Resolution");
        requestedResolution.setItems(RequestedResolution.values());
        requestedResolution.setItemLabelGenerator(ClaimForm::label);
        requestedResolution.setEmptySelectionAllowed(true);
        requestedResolution.setEmptySelectionCaption("None");

        neededBy.setId("needed-by");
        neededBy.setPlaceholder("Choose Date");
        // written ISO, as the lists write their dates
        neededBy.setI18n(new DatePicker.DatePickerI18n().setDateFormat("yyyy-MM-dd"));

        claimedAmount.setId("claimed-amount");
        claimedAmount.setPrefixComponent(new Span("€"));
        var twoDecimals = NumberFormat.getNumberInstance(locale);
        twoDecimals.setMinimumFractionDigits(2);
        claimedAmount.setPlaceholder(twoDecimals.format(0));

        description.setId("description");
        description.setPlaceholder("Write Description");
        description.setMaxLength(DESCRIPTION_LENGTH);
        description.setMinRows(3);
    }

    private void bindFields() {
        binder.forField(customer).asRequired("Choose the customer").bind("customer");
        binder.forField(reportedBy).bind("reportedBy");
        binder.forField(order).bind("order");
        binder.forField(shipment).bind("shipment");
        binder.forField(claimType).asRequired("Choose what the claim is about").bind("claimType");
        binder.forField(priority).asRequired("Choose a priority").bind("priority");
        binder.forField(requestedResolution).bind("requestedResolution");
        binder.forField(neededBy).bind("neededBy");
        binder.forField(claimedAmount)
                .withValidator(amount -> amount == null || amount.signum() >= 0, "The amount cannot be negative")
                .withValidator(amount -> amount == null || amount.scale() <= 2, "Use at most two decimals")
                .bind("claimedAmount");
        binder.forField(description).bind("description");
        clear();
    }

    /** Each choice offers what the one before it leads to: the customer's contacts and orders, the order's shipments. */
    private void cascade() {
        reportedBy.setEnabled(false);
        order.setEnabled(false);
        shipment.setEnabled(false);
        customer.addValueChangeListener(e -> {
            var chosen = e.getValue();
            reportedBy.clear();
            reportedBy.setItems(contactsOf(chosen));
            reportedBy.setEnabled(chosen != null);
            order.clear();
            order.setItems(ordersOf(chosen));
            order.setEnabled(chosen != null);
        });
        // an order that came in one delivery names its shipment too
        order.addValueChangeListener(e -> {
            var chosen = e.getValue();
            var delivered = shipmentsOf(chosen);
            shipment.clear();
            shipment.setItems(delivered);
            shipment.setEnabled(chosen != null);
            if (delivered.size() == 1) {
                shipment.setValue(delivered.getFirst());
            }
        });
    }

    private List<CustomerContactRepository.Option> contactsOf(CustomerRepository.Option customer) {
        return customer == null ? List.of() : contacts.optionsOf(customer.id());
    }

    private List<SalesOrderRepository.Option> ordersOf(CustomerRepository.Option customer) {
        return customer == null ? List.of() : orders.optionsOf(customer.id());
    }

    private List<ShipmentRepository.Option> shipmentsOf(SalesOrderRepository.Option order) {
        return order == null ? List.of() : shipments.optionsOf(order.id());
    }

    private void create() {
        var draft = new Draft();
        if (!binder.writeBeanIfValid(draft)) {
            return;
        }
        var confirmation = Notification.show(confirmation(draft, locale));
        confirmation.addThemeVariants(NotificationVariant.SUCCESS);
        clear();
        fireEvent(new CreatedEvent(this));
    }

    /** Where the value of the field in focus came from, when it came from the AI; see {@link SourceEvent}. */
    Registration addSourceListener(ComponentEventListener<SourceEvent> listener) {
        return addListener(SourceEvent.class, listener);
    }

    /** A claim was created; see {@link CreatedEvent}. */
    Registration addCreatedListener(ComponentEventListener<CreatedEvent> listener) {
        return addListener(CreatedEvent.class, listener);
    }

    /**
     * Follows the field in focus, by the browser's {@code focusin} and {@code focusout}, which every field sends,
     * the radio group among them, and shows its source anew when it gets the focus, loses it, or its value changes:
     * a value the user edits or reverts is no longer the AI's.
     */
    private void followFocus() {
        for (var field : List.<HasValue<?, ?>>of(
                customer,
                reportedBy,
                order,
                shipment,
                claimType,
                priority,
                requestedResolution,
                neededBy,
                claimedAmount,
                description)) {
            var element = ((Component) field).getElement();
            element.addEventListener("focusin", e -> {
                focused = field;
                showSource();
            });
            element.addEventListener("focusout", e -> {
                if (focused == field) {
                    focused = null;
                    showSource();
                }
            });
            field.addValueChangeListener(e -> {
                if (focused == field) {
                    showSource();
                }
            });
        }
    }

    private void showSource() {
        var source = controller == null || focused == null
                ? null
                : controller.getFieldSource(focused).orElse(null);
        fireEvent(new SourceEvent(this, source));
    }

    /** What Create says of a claim: whose it is, what about, how urgent and for how much. */
    static String confirmation(Draft draft, Locale locale) {
        var amount = draft.getClaimedAmount() == null
                ? ""
                : ", " + NumberFormat.getCurrencyInstance(locale).format(draft.getClaimedAmount());
        return "Claim created for " + draft.getCustomer().name() + ": " + label(draft.getClaimType()) + ", "
                + label(draft.getPriority()).toLowerCase(locale) + " priority" + amount
                + ". The demo does not save claims, so the form starts over.";
    }

    /** The snippets a value was read from, quoted, one to a line. */
    private static Component quotes(ValueSource source) {
        var list = new Div();
        list.addClassName("source-extracts");
        source.extracts().forEach(extract -> list.add(new Div("“" + extract.text() + "”")));
        return list;
    }

    /** An enum constant as the screen writes it: {@code MISSING_ITEMS} is "Missing items"; none is "". */
    static String label(Enum<?> constant) {
        if (constant == null) {
            return "";
        }
        var words = constant.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The options whose label for the model contains the filter, ignoring case; all of them for no filter. */
    private static <T> List<T> matching(List<T> options, String filter, int limit, Function<T, String> label) {
        var term = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> label.apply(option).toLowerCase(Locale.ROOT).contains(term))
                .limit(limit)
                .toList();
    }

    private static String customerForTheModel(CustomerRepository.Option customer) {
        return customer.name() + " (" + customer.customerNumber() + ", " + customer.city() + ", @"
                + customer.emailDomain() + ")";
    }

    private static String contactForTheModel(CustomerContactRepository.Option contact) {
        return contact.name() + " (" + contact.role() + ", " + contact.email() + ")";
    }

    private static String orderForTheModel(SalesOrderRepository.Option order) {
        var reference = order.customerReference() == null ? "" : ", customer reference " + order.customerReference();
        var delivered = order.lastDeliveredAt() == null
                ? ", not delivered"
                : ", delivered " + order.lastDeliveredAt().toLocalDate();
        return order.orderNumber() + " (placed " + order.placedAt().toLocalDate() + ", promised "
                + order.promisedDeliveryDate() + delivered + ", net EUR "
                + order.totalNet().toPlainString()
                + reference + ")";
    }

    private static String shipmentForTheModel(ShipmentRepository.Option shipment) {
        var delivered = shipment.deliveredAt() == null
                ? ", not delivered"
                : ", delivered " + shipment.deliveredAt().toLocalDate();
        return shipment.shipmentNumber() + " (" + shipment.carrier() + ", tracking " + shipment.trackingNumber()
                + ", shipped " + shipment.shippedAt().toLocalDate() + delivered + ", " + shipment.palletCount()
                + " pallets)";
    }
}
