package com.vaadin.demo.nordicsupply.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;

import com.vaadin.demo.nordicsupply.ai.Conversations;
import com.vaadin.demo.nordicsupply.config.DisplayLocale;
import com.vaadin.demo.nordicsupply.data.CustomerContactRepository;
import com.vaadin.demo.nordicsupply.data.CustomerRepository;
import com.vaadin.demo.nordicsupply.data.SalesOrderRepository;
import com.vaadin.demo.nordicsupply.data.ShipmentRepository;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;

/**
 * The view offers every example file to drag to the message area or click, each file is there to read and of a type
 * the model reads, and an email reaches the model as its text.
 */
class ClaimsViewTest {

    private static final SignedInUser TEST_USER = new SignedInUser(1, "Test User", Scopes.ALL);

    /** Held here, since the current UI is kept only weakly and a garbage collection would clear it mid-test. */
    private final UI ui = new UI();

    private ClaimsView view;

    @BeforeEach
    void theViewAsTheHeadOfficeSeesIt() {
        ui.setLocale(DisplayLocale.LOCALE);
        UI.setCurrent(ui);
        var currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(TEST_USER);
        when(currentUser.signal()).thenReturn(new SharedValueSignal<>(TEST_USER));
        var conversations = new Conversations(
                ChatClient.builder(mock(ChatModel.class)),
                MessageWindowChatMemory.builder().build());
        view = new ClaimsView(
                mock(CustomerRepository.class),
                mock(CustomerContactRepository.class),
                mock(SalesOrderRepository.class),
                mock(ShipmentRepository.class),
                conversations,
                currentUser);
    }

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void everyExampleFileIsOfferedAndDragsItself() {
        var tiles = descendants(view).filter(ClaimsViewTest::isTile).toList();

        assertThat(tiles)
                .extracting(tile -> DragSource.configure(tile).getDragData())
                .containsExactly((Object[]) ExampleFile.values());
    }

    @ParameterizedTest
    @EnumSource(ExampleFile.class)
    void anExampleFileIsThereAndOfATypeTheModelReads(ExampleFile file) {
        assertThat(file.bytes()).isNotEmpty();
        assertThat(ClaimsView.ACCEPTED).anyMatch(file.fileName()::endsWith);
        assertThat(file.mimeType()).isEqualTo(ClaimsView.mimeTypeOf(file.fileName(), "application/octet-stream"));
    }

    @Test
    void anEmailReachesTheModelAsTextAndAnImageAsTheBrowserSaysItIs() {
        assertThat(ClaimsView.mimeTypeOf("Order 17.EML", "message/rfc822")).isEqualTo("text/plain");
        assertThat(ClaimsView.mimeTypeOf("note.txt", "")).isEqualTo("text/plain");
        assertThat(ClaimsView.mimeTypeOf("scan.pdf", "application/x-pdf")).isEqualTo("application/pdf");
        assertThat(ClaimsView.mimeTypeOf("photo.jpeg", "image/jpeg")).isEqualTo("image/jpeg");
    }

    @Test
    void theReadmeSaysHowAMessageBecomesAClaim() {
        assertThat(view.readme()).contains("Drop the customer's message", "AI badge", "does not save claims");
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(ClaimsViewTest::descendants));
    }

    private static boolean isTile(Component component) {
        return component instanceof Div tile && tile.hasClassName("example-file");
    }
}
