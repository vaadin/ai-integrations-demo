package com.vaadin.demo.nordicsupply.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextFieldBase;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.vaadin.demo.nordicsupply.feedback.FeedbackLimit;
import com.vaadin.demo.nordicsupply.feedback.FeedbackNotSentException;
import com.vaadin.demo.nordicsupply.feedback.SlackFeedback;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;
import com.vaadin.demo.nordicsupply.session.Visit;
import com.vaadin.demo.nordicsupply.ui.TestUi;

/** The dialog sends feedback once it is valid and admitted, and keeps the text when it is not sent. */
class FeedbackDialogTest {

    private static final SignedInUser LARS = new SignedInUser(8, "Lars Nygaard", Scopes.FI_EE);
    private static final UUID VISIT = UUID.randomUUID();

    private final SlackFeedback slackFeedback = mock(SlackFeedback.class);
    private final FeedbackLimit feedbackLimit = mock(FeedbackLimit.class);
    private final Visit visit = mock(Visit.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final List<Runnable> heldSends = new ArrayList<>();

    /** CurrentInstance keeps the UI only weakly, so the test holds it. */
    private UI ui;

    private FeedbackDialog dialog;

    @BeforeEach
    void anOpenDialog() {
        ui = TestUi.withSession();
        when(visit.id()).thenReturn(VISIT);
        when(currentUser.get()).thenReturn(LARS);
        when(feedbackLimit.admit(any())).thenReturn(true);
        dialog = newDialog();
        dialog.open();
    }

    @AfterEach
    void noCurrentInstances() {
        CurrentInstance.clearAll();
    }

    @Test
    void closeKeepsTheDraftForTheNextOpen() {
        // a closed dialog, so that the button is what opens it
        dialog = newDialog();
        var button = new FeedbackButton(dialog);

        button.click();
        feedbackField().setValue("Half a thought");
        closeButton().click();
        button.click();

        assertThat(dialog.isOpened()).isTrue();
        assertThat(feedbackField().getValue()).isEqualTo("Half a thought");
    }

    @Test
    void theButtonTellsScreenReadersThatItOpensADialog() {
        var button = new FeedbackButton(dialog);

        assertThat(button.getElement().getAttribute("aria-haspopup")).isEqualTo("dialog");
    }

    @Test
    void at2000CharactersTheCounterSaysThatMoreTextIsNotAdded() {
        feedbackField().setValue("x".repeat(1999));

        assertThat(feedbackField().getHelperText()).isEqualTo("1999/2000");

        // an emoji is 2 UTF-16 units, so 1000 are 2000
        feedbackField().setValue("😀".repeat(1000));

        assertThat(feedbackField().getHelperText()).isEqualTo("2000/2000. More text is not added.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankTextIsNotSentAndTheFieldSaysWhy(String blank) {
        feedbackField().setValue(blank);

        sendButton().click();

        verifyNoInteractions(slackFeedback, feedbackLimit);
        assertThat(dialog.isOpened()).isTrue();
        assertThat(feedbackField().isInvalid()).isTrue();
        assertThat(feedbackField().getErrorMessage()).isEqualTo("Write your feedback first");
        assertThat(sendButton().isEnabled()).isTrue();
    }

    @Test
    void anEmptyFieldIsNotMarkedWhenTheDialogOpensAgain() {
        sendButton().click();
        closeButton().click();

        dialog.open();

        assertThat(feedbackField().isInvalid()).isFalse();
    }

    @Test
    void aTextOverTheLimitStaysMarkedWhenTheDialogOpensAgain() {
        feedbackField().setValue("x".repeat(2001));
        closeButton().click();

        dialog.open();

        assertThat(feedbackField().isInvalid()).isTrue();

        sendButton().click();

        verifyNoInteractions(slackFeedback);
    }

    @ParameterizedTest
    @MethodSource("rejectedInput")
    void inputThatAFieldRejectsIsNotSentAndStays(String label, String input, String message) {
        feedbackField().setValue("The chart is great.");
        field(label).setValue(input);

        sendButton().click();

        verifyNoInteractions(slackFeedback, feedbackLimit);
        assertThat(field(label).isInvalid()).isTrue();
        assertThat(field(label).getErrorMessage()).isEqualTo(message);
        assertThat(field(label).getValue()).isEqualTo(input);
        assertThat(sendButton().isEnabled()).isTrue();
    }

    static Stream<Arguments> rejectedInput() {
        return Stream.of(
                arguments("Email", "not-an-email", "Enter a valid email address"),
                arguments("Feedback", "x".repeat(2001), "At most 2000 characters"),
                // an emoji is 2 UTF-16 units, so 1001 are 2002
                arguments("Feedback", "😀".repeat(1001), "At most 2000 characters"));
    }

    @Test
    void aTextOfExactly2000CharactersIsSent() {
        var text = "x".repeat(2000);
        feedbackField().setValue(text);

        sendButton().click();

        verify(slackFeedback).send(eq(text), any(), any(), any());
    }

    @Test
    void overTheLimitPerAddressNothingIsSentAndTheTextStays() {
        when(feedbackLimit.admit(any())).thenReturn(false);
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        verifyNoInteractions(slackFeedback);
        assertThat(feedbackField().getValue()).isEqualTo("The chart is great.");
        assertThat(sendError().isVisible()).isTrue();
        assertThat(sendErrorText())
                .isEqualTo("Too much feedback was sent from your network in the last hour. Try again later, or open an"
                        + " issue on GitHub.");
        assertThat(issueLink().getTargetValue()).isEqualTo(AnchorTarget.BLANK);
        assertThat(sendButton().isEnabled()).isTrue();
    }

    @Test
    void nothingCanBeChangedOrClosedUntilTheSendEnds() {
        dialog = dialogThatHoldsItsSends();
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        assertThat(sendButton().getText()).isEqualTo("Sending…");
        assertThat(sendButton().isEnabled()).isFalse();
        assertThat(closeButton().isEnabled()).isFalse();
        assertThat(feedbackField().isReadOnly()).isTrue();
        assertThat(emailField().isReadOnly()).isTrue();
        assertThat(dialog.isCloseOnEsc()).isFalse();
        assertThat(dialog.isCloseOnOutsideClick()).isFalse();

        runHeldSends();

        assertThat(sendButton().getText()).isEqualTo("Send");
        assertThat(sendButton().isEnabled()).isTrue();
        assertThat(closeButton().isEnabled()).isTrue();
        assertThat(feedbackField().isReadOnly()).isFalse();
        assertThat(emailField().isReadOnly()).isFalse();
        assertThat(dialog.isCloseOnEsc()).isTrue();
        assertThat(dialog.isCloseOnOutsideClick()).isTrue();
        assertThat(dialog.isOpened()).isFalse();
    }

    @Test
    void aFailedSendKeepsTheTextAndSaysSoUntilTheDialogOpensAgain() {
        doThrow(new FeedbackNotSentException()).when(slackFeedback).send(any(), any(), any(), any());
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        assertThat(dialog.isOpened()).isTrue();
        runBeforeResponse();
        // an open dialog holds the notification
        assertThat(components(Notification.class)).isEmpty();
        assertThat(feedbackField().getValue()).isEqualTo("The chart is great.");
        assertThat(sendError().isVisible()).isTrue();
        assertThat(sendErrorText())
                .isEqualTo("Your feedback could not be sent. Try again in a moment, or open an issue on GitHub.");
        assertThat(issueLink().getTargetValue()).isEqualTo(AnchorTarget.BLANK);
        assertThat(sendButton().isEnabled()).isTrue();

        closeButton().click();
        dialog.open();

        assertThat(sendError().isVisible()).isFalse();
        assertThat(feedbackField().getValue()).isEqualTo("The chart is great.");
    }

    @Test
    void aFailureAfterTheDialogOpensAgainIsNotASecondFailureInARow() {
        doThrow(new FeedbackNotSentException()).when(slackFeedback).send(any(), any(), any(), any());
        feedbackField().setValue("The chart is great.");
        sendButton().click();
        closeButton().click();
        dialog.open();

        sendButton().click();

        assertThat(sendErrorText())
                .isEqualTo("Your feedback could not be sent. Try again in a moment, or open an issue on GitHub.");
    }

    @Test
    void rewritingTheTextHidesTheSendError() {
        doThrow(new FeedbackNotSentException()).when(slackFeedback).send(any(), any(), any(), any());
        feedbackField().setValue("The chart is great.");
        sendButton().click();

        feedbackField().setValue("The chart is great, but slow.");

        assertThat(sendError().isVisible()).isFalse();
    }

    @Test
    void aSecondFailureInARowSaysThatTheFeedbackStillCouldNotBeSent() {
        doThrow(new FeedbackNotSentException()).when(slackFeedback).send(any(), any(), any(), any());
        dialog = dialogThatHoldsItsSends();
        feedbackField().setValue("The chart is great.");
        sendButton().click();
        runHeldSends();

        sendButton().click();

        assertThat(sendError().isVisible()).isFalse();

        runHeldSends();

        assertThat(sendError().isVisible()).isTrue();
        assertThat(sendErrorText())
                .isEqualTo("Your feedback still could not be sent. Try again later, or open an issue on GitHub.");
        assertThat(issueLink().getTargetValue()).isEqualTo(AnchorTarget.BLANK);
    }

    @Test
    void anUnexpectedErrorUnlocksTheDialogAndShowsTheFailure() {
        doThrow(new IllegalStateException("a bug")).when(slackFeedback).send(any(), any(), any(), any());
        dialog = dialogThatHoldsItsSends();
        feedbackField().setValue("The chart is great.");
        sendButton().click();

        assertThatThrownBy(this::runHeldSends).isInstanceOf(IllegalStateException.class);

        assertThat(sendButton().getText()).isEqualTo("Send");
        assertThat(sendButton().isEnabled()).isTrue();
        assertThat(feedbackField().isReadOnly()).isFalse();
        assertThat(sendError().isVisible()).isTrue();
        assertThat(sendErrorText()).startsWith("Your feedback could not be sent.");
    }

    @Test
    void aPageClosedDuringASendIsIgnored() {
        dialog = dialogThatHoldsItsSends();
        feedbackField().setValue("The chart is great.");
        sendButton().click();

        ui.getInternals().setSession(null);

        assertThatCode(this::runHeldSends).doesNotThrowAnyException();
    }

    @Test
    void aSentFeedbackClosesTheDialogClearsTheTextAndThanksTheVisitor() {
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        verify(slackFeedback).send(eq("The chart is great."), isNull(), any(), eq(LARS));
        assertThat(dialog.isOpened()).isFalse();
        assertThat(feedbackField().getValue()).isEmpty();
        assertThat(feedbackField().isInvalid()).isFalse();
        assertThat(sendButton().isEnabled()).isTrue();
        runBeforeResponse();
        assertThat(notification().getElement().getProperty("text"))
                .isEqualTo("Thank you. Your feedback has been sent.");
        assertThat(notification().getThemeNames()).contains("success");
    }

    @Test
    void theTextIsSentWithoutOuterSpaces() {
        feedbackField().setValue("  The chart is great.  ");

        sendButton().click();

        verify(slackFeedback).send(eq("The chart is great."), any(), any(), any());
    }

    @Test
    void theEmailGoesToTheTeamAndStaysAfterASend() {
        feedbackField().setValue("The chart is great.");
        emailField().setValue("visitor@example.com");

        sendButton().click();

        verify(slackFeedback).send(eq("The chart is great."), eq("visitor@example.com"), any(), any());
        assertThat(emailField().getValue()).isEqualTo("visitor@example.com");
    }

    @Test
    void thePageIsTheOneWhereWritingStarted() {
        TestUi.showRoute(ui, "dashboard");
        feedbackField().setValue("The chart");
        TestUi.showRoute(ui, "orders");
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        verify(slackFeedback).send(any(), any(), eq("/dashboard"), any());

        dialog.open();
        feedbackField().setValue("The list too.");
        sendButton().click();

        verify(slackFeedback).send(any(), any(), eq("/orders"), any());
    }

    @ParameterizedTest
    @CsvSource({"ai.demo.vaadin.com, 443, ai.demo.vaadin.com/dashboard", "localhost, 8080, localhost:8080/dashboard"})
    void thePageStartsWithTheHostOfTheRequest(String serverName, int port, String page) {
        var request = mock(VaadinServletRequest.class);
        when(request.getServerName()).thenReturn(serverName);
        when(request.getServerPort()).thenReturn(port);
        CurrentInstance.set(VaadinRequest.class, request);
        TestUi.showRoute(ui, "dashboard");
        feedbackField().setValue("The chart is great.");

        sendButton().click();

        verify(slackFeedback).send(any(), any(), eq(page), any());
    }

    /** Sends run at once, on the test thread. */
    private FeedbackDialog newDialog() {
        return new FeedbackDialog(slackFeedback, feedbackLimit, visit, currentUser, Runnable::run);
    }

    /** An open dialog whose sends wait until {@link #runHeldSends()}. */
    private FeedbackDialog dialogThatHoldsItsSends() {
        var holding = new FeedbackDialog(slackFeedback, feedbackLimit, visit, currentUser, heldSends::add);
        holding.open();
        return holding;
    }

    private void runHeldSends() {
        var sends = List.copyOf(heldSends);
        heldSends.clear();
        sends.forEach(Runnable::run);
    }

    private TextArea feedbackField() {
        return components(TextArea.class).findFirst().orElseThrow();
    }

    private EmailField emailField() {
        return components(EmailField.class).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private TextFieldBase<?, String> field(String label) {
        return components(TextFieldBase.class)
                .filter(field -> label.equals(field.getLabel()))
                .map(field -> (TextFieldBase<?, String>) field)
                .findFirst()
                .orElseThrow();
    }

    /** Found by place, not by text, because Send says "Sending…" while it sends. */
    private Button sendButton() {
        return components(Button.class).toList().getLast();
    }

    private Button closeButton() {
        return components(Button.class).toList().getFirst();
    }

    private Div sendError() {
        return components(Div.class)
                .filter(div -> "alert".equals(div.getElement().getAttribute("role")))
                .findFirst()
                .orElseThrow();
    }

    private String sendErrorText() {
        return sendError().getElement().getTextRecursively();
    }

    private Anchor issueLink() {
        return sendError()
                .getChildren()
                .filter(Anchor.class::isInstance)
                .map(Anchor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** Runs what Flow runs just before the response, such as adding a notification. */
    private void runBeforeResponse() {
        // an open dialog then attaches its issue link, which reads the safe URL schemes from the configuration
        when(ui.getSession().getService().getDeploymentConfiguration())
                .thenReturn(mock(DeploymentConfiguration.class, CALLS_REAL_METHODS));
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
    }

    /** Flow adds it to the UI, because a sent feedback closes the dialog. */
    private Notification notification() {
        return ui.getChildren()
                .filter(Notification.class::isInstance)
                .map(Notification.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** The dialog's components of the given type, the footer's included. */
    private <T extends Component> Stream<T> components(Class<T> type) {
        return Stream.concat(
                        dialog.getElement().getChildren(),
                        dialog.getFooter().getElement().getChildren())
                .flatMap(child -> child.getComponent().stream())
                .filter(type::isInstance)
                .map(type::cast);
    }
}
