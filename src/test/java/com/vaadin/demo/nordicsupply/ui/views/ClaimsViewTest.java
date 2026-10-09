package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.node.NullNode;

import com.vaadin.demo.nordicsupply.ai.LLMProviders;
import com.vaadin.demo.nordicsupply.ai.ScriptedProvider;
import com.vaadin.demo.nordicsupply.config.DisplayLocale;
import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.examples.ExampleDocument;
import com.vaadin.demo.nordicsupply.examples.ExampleDocuments;
import com.vaadin.demo.nordicsupply.examples.PaintedExamples;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;
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
        return viewFor(user, new LLMProviders(ChatClient.builder(mock(ChatModel.class))));
    }

    private static ClaimsView viewFor(SignedInUser user, LLMProviders providers) {
        var currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(user);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(user));
        return new ClaimsView(
                mock(CustomerRepository.class),
                mock(CustomerContactRepository.class),
                mock(SalesOrderRepository.class),
                mock(ShipmentRepository.class),
                providers,
                currentUser,
                EXAMPLES);
    }

    /** Each example tile's button, which reads the example when clicked and carries it when dragged. */
    private static List<Component> examplesOf(ClaimsView view) {
        return ComponentTree.descendants(view).filter(ClaimsViewTest::isExample).toList();
    }

    private static boolean isExample(Component component) {
        return component instanceof NativeButton use && use.hasClassName("file-use");
    }

    /** Plays the browser for one round trip: every JavaScript call the page waits on returns, with nothing. */
    private static void browserAnswers(UI ui) {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(PendingJavaScriptInvocation::isSubscribed)
                .forEach(call -> call.complete(NullNode.getInstance()));
    }
}
