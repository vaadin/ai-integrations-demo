package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.upload.UploadDropZone;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.StateNode;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.internal.nodefeature.VirtualChildrenList;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

import com.vaadin.demo.nordicsupply.ai.LLMProviders;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider.ToolCall;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudget;
import com.vaadin.demo.nordicsupply.ai.budget.AIBudgetProperties.Address;
import com.vaadin.demo.nordicsupply.ai.budget.Budgets;
import com.vaadin.demo.nordicsupply.config.DisplayLocale;
import com.vaadin.demo.nordicsupply.data.ActivityLog;
import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.domain.ActivityView;
import com.vaadin.demo.nordicsupply.domain.ClaimType;
import com.vaadin.demo.nordicsupply.examples.ExampleDocument;
import com.vaadin.demo.nordicsupply.examples.ExampleDocuments;
import com.vaadin.demo.nordicsupply.examples.PaintedExamples;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.TestUi;
import com.vaadin.demo.nordicsupply.ui.components.AIPausedNotice;

/**
 * The view offers the examples of the user's region to drag to the message area or click, each a document the view
 * accepts, and an email reaches the model as its text; the model reads a message on the demo's day.
 */
class ClaimsViewTest {

    private static final SignedInUser HEAD_OFFICE = new SignedInUser(1, "Test User", Scopes.ALL);
    private static final SignedInUser NORTH = new SignedInUser(2, "Test User", Scopes.SE_NO);

    private static final ExampleDocuments EXAMPLES = PaintedExamples.ALL;

    /** The event the upload's connector sends for a file the browser did not send. */
    private static final String FILE_REJECT = "file-reject";

    /** Held here, since the current UI is kept only weakly and a garbage collection would clear it mid-test. */
    private final UI ui = new UI();

    @BeforeEach
    void aUi() {
        ui.setLocale(DisplayLocale.LOCALE);
        UI.setCurrent(ui);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void theHeadOfficeIsOfferedEveryExampleAndEachDragsItself() {
        assertThat(examplesOf(viewFor(HEAD_OFFICE)))
                .extracting(tile -> DragSource.configure(tile).getDragData())
                .containsExactlyElementsOf(EXAMPLES.all());
    }

    @Test
    void aRegionIsOfferedTheExamplesOfItsCountries() {
        assertThat(examplesOf(viewFor(NORTH)))
                .extracting(
                        tile -> ((ExampleDocument) DragSource.configure(tile).getDragData()).name())
                .containsExactly("Missing cartons.png", "Quality defect.jpg");
    }

    @Test
    void everyExampleIsAFileTheViewAcceptsAsWhatItIs() {
        assertThat(EXAMPLES.all()).allSatisfy(example -> {
            assertThat(ClaimsView.ACCEPTED).anyMatch(example.name()::endsWith);
            assertThat(ClaimsView.mimeTypeOf(example.name()))
                    .isEqualTo(example.attachment().mimeType());
        });
    }

    @Test
    void anEmailReachesTheModelAsTextAndTheOthersAsWhatTheyAre() {
        assertThat(ClaimsView.mimeTypeOf("Order 17.EML")).isEqualTo("text/plain");
        assertThat(ClaimsView.mimeTypeOf("note.txt")).isEqualTo("text/plain");
        assertThat(ClaimsView.mimeTypeOf("scan.pdf")).isEqualTo("application/pdf");
        assertThat(ClaimsView.mimeTypeOf("photo.jpeg")).isEqualTo("image/jpeg");
        assertThat(ClaimsView.mimeTypeOf("portal.png")).isEqualTo("image/png");
        assertThat(ClaimsView.ACCEPTED)
                .allSatisfy(extension ->
                        assertThat(ClaimsView.mimeTypeOf("file" + extension)).isNotBlank());
    }

    /**
     * A message is read on the dataset's day, which a message without a date of its own counts from, and not on the
     * server's: the session context of the turn that reads it says so.
     */
    @Test
    void aMessageIsReadOnTheDatasetsDay() {
        var withSession = TestUi.withSession();
        withSession.setLocale(DisplayLocale.LOCALE);
        var model = new ScriptedProvider("The form is filled in.");
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(model);
        var view = viewFor(HEAD_OFFICE, providers);
        withSession.add(view);
        var example = examplesOf(view).getFirst();

        ComponentUtil.fireEvent(example, new ClickEvent<>(example));
        browserAnswers(withSession);

        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.attachments())
                    .extracting(AIAttachment::name)
                    .containsExactly(
                            ((ExampleDocument) DragSource.configure(example).getDragData()).name());
            // the session context, which the providers add to the user message for the model
            assertThat(request.sessionContext())
                    .contains("Current date and time: 2026-09-07T")
                    .contains("(Monday, Europe/Helsinki)");
        });
    }

    /** The budget admits a message before it is read: refused, the visitor is told why and nothing reaches the model. */
    @Test
    void aMessageTheBudgetRefusesIsAnsweredWithANoticeAndNeverReachesTheModel() {
        var withSession = TestUi.withSession();
        var model = new ScriptedProvider("The form is filled in.");
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(model);
        var view = viewFor(HEAD_OFFICE, providers, Budgets.disabled());
        withSession.add(view);
        var example = examplesOf(view).getFirst();

        ComponentUtil.fireEvent(example, new ClickEvent<>(example));
        browserAnswers(withSession);

        assertThat(model.requests()).isEmpty();
        assertThat(notices(withSession))
                .containsExactly(
                        "The AI features are switched off right now; the rest of the application works as usual.");
        // and the page said so before the click
        assertThat(pausedNoticeOf(view))
                .satisfies(notice -> assertThat(notice.isVisible()).isTrue())
                .extracting(AIPausedNotice::getText)
                .isEqualTo("The AI features are switched off right now; the rest of the application works as usual.");
    }

    @Test
    void thePageSaysNothingOfTheAIWhileItAnswers() {
        assertThat(pausedNoticeOf(viewFor(HEAD_OFFICE)).isVisible()).isFalse();
    }

    private static AIPausedNotice pausedNoticeOf(ClaimsView view) {
        return (AIPausedNotice) ComponentTree.descendants(view)
                .filter(AIPausedNotice.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    /**
     * A turn is counted when its model request starts, one round trip after the message was chosen, not before: a
     * browser that never answers that round trip holds no turn, so another tab of the same visitor is not held back.
     */
    @Test
    void aMessageWhoseBrowserNeverAnswersHoldsNoTurn() {
        var withSession = TestUi.withSession();
        // a provider serves one orchestrator: one per view
        var goneModel = new ScriptedProvider("The form is filled in.");
        var stayingModel = new ScriptedProvider("The form is filled in.");
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(goneModel, stayingModel);
        var oneAtATime = Budgets.withAddress(new Address(120, 1));
        var gone = viewFor(HEAD_OFFICE, providers, oneAtATime);
        var staying = viewFor(HEAD_OFFICE, providers, oneAtATime);
        withSession.add(gone, staying);

        var left = examplesOf(gone).getFirst();
        ComponentUtil.fireEvent(left, new ClickEvent<>(left));
        var chosen = examplesOf(staying).getFirst();
        ComponentUtil.fireEvent(chosen, new ClickEvent<>(chosen));
        browserAnswersOnly(withSession, staying);

        assertThat(goneModel.requests()).isEmpty();
        assertThat(stayingModel.requests()).hasSize(1);
        assertThat(notices(withSession)).isEmpty();
    }

    /** A pasted text is read as a message of its own; one over the cap is refused with the reason, before it is one. */
    @Test
    void aPastedTextIsReadAsAMessageAndOneOverTheCapIsRefused() {
        var withSession = TestUi.withSession();
        var model = new ScriptedProvider("The form is filled in.");
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(model);
        var view = viewFor(HEAD_OFFICE, providers);
        withSession.add(view);

        paste(view, "x".repeat(20_001));
        browserAnswers(withSession);

        assertThat(model.requests()).isEmpty();
        assertThat(notices(withSession))
                .containsExactly("A message can have at most 20000 characters of text; this one has 20001.");

        paste(view, "Hello,\n\nthree cartons were missing from order SO-1.");
        browserAnswers(withSession);

        assertThat(model.requests())
                .singleElement()
                .satisfies(request -> assertThat(request.attachments())
                        .extracting(AIAttachment::name, AIAttachment::mimeType)
                        .containsExactly(tuple("Pasted text", "text/plain")));
    }

    /** A file the browser does not send, too large or of a kind the form does not take, is answered with the cap set. */
    @Test
    void aFileTheBrowserRejectsIsAnsweredWithTheCapAsSet() {
        var withSession = TestUi.withSession();
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(new ScriptedProvider("The form is filled in."));
        var view = viewFor(HEAD_OFFICE, providers, Budgets.withMaxFileSize(DataSize.ofKilobytes(300)));
        withSession.add(view);

        browserRejects(view, "scan.pdf", "fileIsTooBig");
        browserAnswers(withSession);

        assertThat(notices(withSession))
                .containsExactly("The AI reads emails, text files, PDFs and images of up to 300 KB.");
    }

    /**
     * Reading a message is a turn in the activity log, recorded with the message's name; the form as the AI left it is
     * what the model proposed.
     */
    @Test
    void readingAMessageIsRecordedWithItsNameAndTheFormTheAiFilledIn() {
        var withSession = TestUi.withSession();
        withSession.setLocale(DisplayLocale.LOCALE);
        var model = new ScriptedProvider(
                "The form is filled in.",
                new ToolCall("fill_form", "{\"values\":{\"description\":\"Two cartons missing.\"}}"));
        var providers = mock(LLMProviders.class);
        when(providers.forSingleTurns()).thenReturn(model);
        var log = mock(ActivityLog.class);
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(5L);
        var view = viewFor(HEAD_OFFICE, providers, log);
        withSession.add(view);
        var example = examplesOf(view).getFirst();
        var name = ((ExampleDocument) DragSource.configure(example).getDragData()).name();

        ComponentUtil.fireEvent(example, new ClickEvent<>(example));
        browserAnswers(withSession);

        verify(log)
                .asked(
                        any(),
                        eq(ActivityView.CLAIM_FORM),
                        argThat(prompt ->
                                prompt.startsWith("Fill in the claim form from the attached customer message.")
                                        && prompt.endsWith("Attached: " + name)),
                        startsWith("The attached message"));
        verify(log).ended(eq(5L), eq("The form is filled in."), any(), any(), any());
        verify(log).proposed(5L, "Priority: Normal\nDescription: Two cartons missing.");
    }

    /** Creating a claim is the user keeping what the form holds, the AI's values or their own. */
    @Test
    void aCreatedClaimIsRecordedAsKept() {
        var log = mock(ActivityLog.class);
        var view = viewFor(HEAD_OFFICE, new LLMProviders(ChatClient.builder(mock(ChatModel.class))), log);
        var withSession = TestUi.withSession();
        withSession.setLocale(DisplayLocale.LOCALE);
        withSession.add(view);
        field(view, "customer", ComboBox.class)
                .setValue(new CustomerRepository.Option(
                        1186, "C-11186", "Aalborg Trekking ApS", "Aalborg", "aalborg.trekking.example"));
        field(view, "claim-type", Select.class).setValue(ClaimType.MISSING_ITEMS);

        ComponentTree.descendants(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Create Claim".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

        verify(log)
                .applied(
                        any(),
                        eq(ActivityView.CLAIM_FORM),
                        eq("Customer: Aalborg Trekking ApS\nClaim Type: Missing items\nPriority: Normal"));
    }

    @Test
    void theReadmeSaysHowAMessageBecomesAClaim() {
        assertThat(viewFor(HEAD_OFFICE).readmeSections())
                .extracting(section -> section.content())
                .satisfiesExactly(
                        introduction -> assertThat(introduction).contains("customer message", "AI model"),
                        confidence ->
                                assertThat(confidence).contains("AI badge", "confidence indicator", "exact match"),
                        languages -> assertThat(languages).contains("always reported in English", "translation"),
                        kept -> assertThat(kept).contains("name and size", "never its contents", "90 days"));
    }

    @Test
    void theReadmePointsAtTheDocumentationOfTheFormFillerAndOfAttachments() {
        assertThat(viewFor(HEAD_OFFICE).readMore())
                .extracting(link -> link.text())
                .contains("AI Form Filler", "File attachments");
    }

    private static ClaimsView viewFor(SignedInUser user) {
        return viewFor(user, new LLMProviders(ChatClient.builder(mock(ChatModel.class))));
    }

    private static ClaimsView viewFor(SignedInUser user, LLMProviders providers) {
        return viewFor(user, providers, Budgets.budget());
    }

    private static ClaimsView viewFor(SignedInUser user, LLMProviders providers, AIBudget budget) {
        return viewFor(user, providers, budget, mock(ActivityLog.class));
    }

    private static ClaimsView viewFor(SignedInUser user, LLMProviders providers, ActivityLog log) {
        return viewFor(user, providers, Budgets.budget(), log);
    }

    private static ClaimsView viewFor(SignedInUser user, LLMProviders providers, AIBudget budget, ActivityLog log) {
        var currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(user);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(user));
        return new ClaimsView(
                mock(CustomerRepository.class),
                mock(CustomerContactRepository.class),
                mock(SalesOrderRepository.class),
                mock(ShipmentRepository.class),
                providers,
                budget,
                currentUser,
                EXAMPLES,
                new Visit(log, Budgets.NO_USAGE_LOG));
    }

    /** The notices shown on the UI, oldest first. */
    private static List<String> notices(UI ui) {
        return ui.getChildren()
                .filter(Notification.class::isInstance)
                .map(notice -> notice.getElement().getProperty("text"))
                .toList();
    }

    /** Pastes text into the message area, as the browser reports a paste without files. */
    private static void paste(ClaimsView view, String text) {
        var zone = ComponentTree.descendants(view)
                .filter(UploadDropZone.class::isInstance)
                .findFirst()
                .orElseThrow()
                .getElement();
        var data = JsonMapper.builder().build().createObjectNode();
        data.put("event.clipboardData.getData('text/plain')", text);
        // the listener's filter, which the browser evaluates and sends along: no files in the paste
        data.put("event.clipboardData.files.length === 0", true);
        zone.getNode().getFeature(ElementListenerMap.class).fireEvent(new DomEvent(zone, "paste", data));
    }

    /** The browser reports a file it did not send, as the upload manager's connector on the view hears it. */
    private static void browserRejects(ClaimsView view, String fileName, String reason) {
        Iterable<StateNode> virtualChildren =
                view.getElement().getNode().getFeature(VirtualChildrenList.class)::iterator;
        var connector = StreamSupport.stream(virtualChildren.spliterator(), false)
                .map(Element::get)
                .filter(element -> !element.getNode()
                        .getFeature(ElementListenerMap.class)
                        .getExpressions(FILE_REJECT)
                        .isEmpty())
                .findFirst()
                .orElseThrow();
        var data = JsonMapper.builder().build().createObjectNode();
        data.put("event.detail.fileName", fileName);
        data.put("event.detail.errorMessage", reason);
        connector.getNode().getFeature(ElementListenerMap.class).fireEvent(new DomEvent(connector, FILE_REJECT, data));
    }

    /** Each example tile's button, which reads the example when clicked and carries it when dragged. */
    private static List<Component> examplesOf(ClaimsView view) {
        return ComponentTree.descendants(view).filter(ClaimsViewTest::isExample).toList();
    }

    private static boolean isExample(Component component) {
        return component instanceof NativeButton use && use.hasClassName("file-use");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends HasValue> T field(ClaimsView view, String id, Class<T> type) {
        return ComponentTree.descendants(view)
                .filter(component -> component.getId().filter(id::equals).isPresent())
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /** The browser answers every pending call, and what the answers set up for the next response is run as well. */
    private static void browserAnswers(UI ui) {
        browserAnswers(ui, call -> true);
    }

    /** The browser answers the given view's calls alone; the others are never answered, as by a browser that went. */
    private static void browserAnswersOnly(UI ui, ClaimsView view) {
        browserAnswers(ui, call -> call.getOwner() == view.getElement().getNode());
    }

    private static void browserAnswers(UI ui, Predicate<PendingJavaScriptInvocation> answered) {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(PendingJavaScriptInvocation::isSubscribed)
                .filter(answered)
                .forEach(call -> call.complete(NullNode.getInstance()));
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
    }
}
