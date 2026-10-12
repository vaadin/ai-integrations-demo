package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.node.NullNode;

import com.vaadin.demo.nordicsupply.ai.Conversations;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider.ToolCall;
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

/**
 * The view offers the examples of the user's region to drag to the message area or click, each a document the view
 * accepts, and an email reaches the model as its text; the model reads a message on the demo's day.
 */
class ClaimsViewTest {

    private static final SignedInUser HEAD_OFFICE = new SignedInUser(1, "Test User", Scopes.ALL);
    private static final SignedInUser NORTH = new SignedInUser(2, "Test User", Scopes.SE_NO);

    private static final ExampleDocuments EXAMPLES = PaintedExamples.ALL;

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
        var conversations = mock(Conversations.class);
        when(conversations.standalone()).thenReturn(model);
        var view = viewFor(HEAD_OFFICE, conversations);
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
        var conversations = mock(Conversations.class);
        when(conversations.standalone()).thenReturn(model);
        var log = mock(ActivityLog.class);
        when(log.asked(any(), any(), anyString(), anyString())).thenReturn(5L);
        var view = viewFor(HEAD_OFFICE, conversations, log);
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
        var view = viewFor(
                HEAD_OFFICE,
                new Conversations(
                        ChatClient.builder(mock(ChatModel.class)),
                        MessageWindowChatMemory.builder().build()),
                log);
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
                        languages -> assertThat(languages).contains("always reported in English", "translation"));
    }

    @Test
    void theReadmePointsAtTheDocumentationOfTheFormFillerAndOfAttachments() {
        assertThat(viewFor(HEAD_OFFICE).readMore())
                .extracting(link -> link.text())
                .contains("AI Form Filler", "File attachments");
    }

    private static ClaimsView viewFor(SignedInUser user) {
        return viewFor(
                user,
                new Conversations(
                        ChatClient.builder(mock(ChatModel.class)),
                        MessageWindowChatMemory.builder().build()));
    }

    private static ClaimsView viewFor(SignedInUser user, Conversations conversations) {
        return viewFor(user, conversations, mock(ActivityLog.class));
    }

    private static ClaimsView viewFor(SignedInUser user, Conversations conversations, ActivityLog log) {
        var currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(user);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(user));
        return new ClaimsView(
                mock(CustomerRepository.class),
                mock(CustomerContactRepository.class),
                mock(SalesOrderRepository.class),
                mock(ShipmentRepository.class),
                conversations,
                currentUser,
                EXAMPLES,
                new Visit(log));
    }

    /** Each example tile's button, which reads the example when clicked and carries it when dragged. */
    private static List<Component> examplesOf(ClaimsView view) {
        return ComponentTree.descendants(view).filter(ClaimsViewTest::isExample).toList();
    }

    private static boolean isExample(Component component) {
        return component instanceof NativeButton use && use.hasClassName("file-use");
    }

    /** Plays the browser for one round trip: every JavaScript call the page waits on returns, with nothing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends HasValue> T field(ClaimsView view, String id, Class<T> type) {
        return ComponentTree.descendants(view)
                .filter(component -> component.getId().filter(id::equals).isPresent())
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private static void browserAnswers(UI ui) {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(PendingJavaScriptInvocation::isSubscribed)
                .forEach(call -> call.complete(NullNode.getInstance()));
    }
}
