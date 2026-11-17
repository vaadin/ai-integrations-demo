package com.vaadin.demo.nordicsupply.ui.components;

import java.util.concurrent.Executor;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.server.VaadinServletRequest;

import com.vaadin.demo.nordicsupply.ai.budget.Caller;
import com.vaadin.demo.nordicsupply.feedback.FeedbackLimit;
import com.vaadin.demo.nordicsupply.feedback.FeedbackNotSentException;
import com.vaadin.demo.nordicsupply.feedback.SlackFeedback;
import com.vaadin.demo.nordicsupply.session.CurrentUser;
import com.vaadin.demo.nordicsupply.session.Visit;

public class FeedbackDialog extends Dialog {

    private static final int MAX_FEEDBACK_LENGTH = 2000;
    // the longest address that SMTP allows
    private static final int MAX_EMAIL_LENGTH = 254;

    private final ValidatableTextArea feedbackField = new ValidatableTextArea();
    private final EmailField emailField = new EmailField("Email");
    private final Div sendError = new Div();
    private final Button closeButton = new Button("Close", event -> close());
    private final Button sendButton = new Button("Send");

    private final SlackFeedback slackFeedback;
    private final FeedbackLimit feedbackLimit;
    private final Visit visit;
    private final CurrentUser currentUser;
    private final Executor executor;

    private String startPage;
    private boolean lastSendFailed;

    public FeedbackDialog(
            SlackFeedback slackFeedback, FeedbackLimit feedbackLimit, Visit visit, CurrentUser currentUser) {
        this(slackFeedback, feedbackLimit, visit, currentUser, Thread::startVirtualThread);
    }

    /** Tests pass an executor that runs each send at once or holds it. */
    FeedbackDialog(
            SlackFeedback slackFeedback,
            FeedbackLimit feedbackLimit,
            Visit visit,
            CurrentUser currentUser,
            Executor executor) {
        this.slackFeedback = slackFeedback;
        this.feedbackLimit = feedbackLimit;
        this.visit = visit;
        this.currentUser = currentUser;
        this.executor = executor;
        addClassName("feedback");
        setHeaderTitle("Send Feedback");
        setWidth("32rem");
        setMaxWidth("100%");
        // a fixed top, so that the dialog does not move while the text area grows. app-rail.css shortens its
        // max-height to match.
        setTop("10vh");

        var intro = new Paragraph(
                "Tell the Vaadin team behind this demo what works, what does not, or what you would like.");

        feedbackField.setLabel("Feedback");
        feedbackField.setRequired(true);
        feedbackField.setMaxLength(MAX_FEEDBACK_LENGTH);
        feedbackField.setI18n(new TextArea.TextAreaI18n()
                .setRequiredErrorMessage("Write your feedback first")
                .setMaxLengthErrorMessage("At most " + MAX_FEEDBACK_LENGTH + " characters"));
        feedbackField.setMinRows(4);
        feedbackField.setMaxRows(10);
        feedbackField.setWidthFull();
        // the dialog moves the focus to a field with autofocus each time it opens
        feedbackField.setAutofocus(true);
        feedbackField.setValueChangeMode(ValueChangeMode.EAGER);
        feedbackField.setHelperText(counterText(""));
        feedbackField.addValueChangeListener(event -> {
            feedbackField.setHelperText(counterText(event.getValue()));
            sendError.setVisible(false);
            var writingStarts =
                    event.getOldValue().isEmpty() && !event.getValue().isEmpty();
            if (writingStarts) {
                startPage = currentPage();
            }
        });

        emailField.setMaxLength(MAX_EMAIL_LENGTH);
        emailField.setI18n(new EmailField.EmailFieldI18n()
                .setPatternErrorMessage("Enter a valid email address")
                .setMaxLengthErrorMessage("At most " + MAX_EMAIL_LENGTH + " characters"));
        emailField.setHelperText("Optional. Add it if the team may write to you about your feedback.");
        emailField.setAutocomplete(Autocomplete.EMAIL);
        emailField.setWidthFull();

        var notice = new Paragraph("The team receives your feedback, your email if you give one, the page where you"
                + " started writing, and the demo user you are signed in as.");
        notice.addClassName("notice");
        sendError.addClassName("send-error");
        sendError.getElement().setAttribute("role", "alert");
        sendError.setVisible(false);
        add(intro, feedbackField, emailField, notice, sendError);

        closeButton.addThemeVariants(ButtonVariant.TERTIARY);
        sendButton.addThemeVariants(ButtonVariant.PRIMARY);
        sendButton.setDisableOnClick(true);
        sendButton.addClickListener(event -> send());
        getFooter().add(closeButton, sendButton);

        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                sendError.setVisible(false);
                lastSendFailed = false;
                // clear the mark that a blank Send left, but keep the mark on a text that is too long
                if (feedbackField.isEmpty()) {
                    feedbackField.setInvalid(false);
                }
            }
        });
    }

    private static String counterText(String text) {
        // UTF-16 units, as the field's own length check counts them. The setMaxLength Javadoc says code points, which
        // is wrong.
        var counter = text.length() + "/" + MAX_FEEDBACK_LENGTH;
        // the browser cuts a longer paste without a message
        return text.length() == MAX_FEEDBACK_LENGTH ? counter + ". More text is not added." : counter;
    }

    /** The host of the request without the scheme, and the route of the shown view. */
    private static String currentPage() {
        var route = "/" + UI.getCurrent().getActiveViewLocation().getPath();
        var request = VaadinServletRequest.getCurrent();
        if (request == null) {
            return route;
        }
        // server.forward-headers-strategy=native makes Tomcat read the host and port from a trusted proxy's headers
        var port = request.getServerPort();
        var host = port == 80 || port == 443 ? request.getServerName() : request.getServerName() + ":" + port;
        return host + route;
    }

    private void send() {
        if (!validateAndAdmit()) {
            // setDisableOnClick disabled the button before this listener ran
            sendButton.setEnabled(true);
            return;
        }
        // the background thread has no current UI, session or request, so read everything here
        var text = feedbackField.getValue().trim();
        var email = emailField.isEmpty() ? null : emailField.getValue();
        var page = startPage;
        var demoUser = currentUser.get();
        SerializableConsumer<Boolean> showOutcome = UI.getCurrent().accessLater(this::showOutcome, () -> {
            // a closed page has nobody to tell
        });
        setSending(true);
        executor.execute(() -> {
            var sent = false;
            try {
                slackFeedback.send(text, email, page, demoUser);
                sent = true;
            } catch (FeedbackNotSentException e) {
                // SlackFeedback has logged why
            } finally {
                // also after any other exception, which then goes on to the thread's default handler
                showOutcome.accept(sent);
            }
        });
    }

    private boolean validateAndAdmit() {
        sendError.setVisible(false);
        if (feedbackField.getValue().trim().isEmpty()) {
            // clear() drops the spaces. On an empty field it changes no value, so the field runs no required check
            // by itself.
            feedbackField.clear();
            feedbackField.validate();
        }
        // The fields check their length and the email pattern on the server, whatever the browser sent.
        if (feedbackField.isInvalid()) {
            feedbackField.focus();
            return false;
        }
        if (emailField.isInvalid()) {
            emailField.focus();
            return false;
        }
        if (!feedbackLimit.admit(Caller.current(visit.id()))) {
            showSendError("Too much feedback was sent from your network in the last hour. Try again later, or");
            return false;
        }
        return true;
    }

    /** Shows the sentence with the link to the GitHub issues at its end. */
    private void showSendError(String textBeforeLink) {
        sendError.removeAll();
        sendError.add(
                new Text(textBeforeLink + " "),
                new Anchor(
                        "https://github.com/vaadin/ai-integrations-demo/issues",
                        "open an issue on GitHub",
                        AnchorTarget.BLANK),
                new Text("."));
        sendError.setVisible(true);
    }

    private void setSending(boolean sending) {
        sendButton.setText(sending ? "Sending…" : "Send");
        sendButton.setEnabled(!sending);
        closeButton.setEnabled(!sending);
        feedbackField.setReadOnly(sending);
        emailField.setReadOnly(sending);
        setCloseOnEsc(!sending);
        setCloseOnOutsideClick(!sending);
    }

    private void showOutcome(boolean sent) {
        setSending(false);
        if (!sent) {
            // other words for a repeated failure, because the same line again looks as if nothing happened
            showSendError(
                    lastSendFailed
                            ? "Your feedback still could not be sent. Try again later, or"
                            : "Your feedback could not be sent. Try again in a moment, or");
            lastSendFailed = true;
            return;
        }
        feedbackField.clear();
        // clear() runs the required check, which marks the empty field invalid
        feedbackField.setInvalid(false);
        close();
        Notification.show("Thank you. Your feedback has been sent.").addThemeVariants(NotificationVariant.SUCCESS);
    }

    /** Makes the protected validate() callable. */
    private static final class ValidatableTextArea extends TextArea {

        @Override
        public void validate() {
            super.validate();
        }
    }
}
