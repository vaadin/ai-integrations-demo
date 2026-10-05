package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;

import com.vaadin.demo.nordicsupply.ai.Conversations;
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

/**
 * The view offers the examples of the user's region to drag to the message area or click, each a document the view
 * accepts, and an email reaches the model as its text.
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

    @Test
    void theReadmeSaysHowAMessageBecomesAClaim() {
        assertThat(viewFor(HEAD_OFFICE).readme())
                .contains("Drop the customer's message", "AI badge", "outlined", "does not save claims");
    }

    private static ClaimsView viewFor(SignedInUser user) {
        var currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(user);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(user));
        var conversations = new Conversations(
                ChatClient.builder(mock(ChatModel.class)),
                MessageWindowChatMemory.builder().build());
        return new ClaimsView(
                mock(CustomerRepository.class),
                mock(CustomerContactRepository.class),
                mock(SalesOrderRepository.class),
                mock(ShipmentRepository.class),
                conversations,
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
}
