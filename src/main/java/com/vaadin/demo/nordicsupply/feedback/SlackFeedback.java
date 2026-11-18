package com.vaadin.demo.nordicsupply.feedback;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.vaadin.demo.nordicsupply.session.CurrentUser;

/** Posts visitor feedback to the team's Slack channel through an incoming webhook. */
@Component
public class SlackFeedback {

    /** Holds no visitor text, because a notification shows the first line. */
    private static final String FIRST_LINE = "New feedback from the AI use case demos";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /** A Slack error code, such as {@code no_service}. */
    private static final Pattern SLACK_ERROR = Pattern.compile("[a-z_]{1,64}");

    private static final Logger LOG = LoggerFactory.getLogger(SlackFeedback.class);

    /**
     * The URL is the secret, because anyone who has it can post to the channel. It must never reach a log or an
     * exception. {@code null} when feedback is off.
     */
    private final URI webhook;

    private final RestClient client;

    @Autowired
    public SlackFeedback(@Value("${app.feedback.slack-webhook-url:}") String url, RestClient.Builder builder) {
        // timeouts on this client only, because the spring.http.clients.* properties would change every client
        this(
                url,
                builder.requestFactory(ClientHttpRequestFactoryBuilder.detect()
                                .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                        .build());
    }

    /**
     * Tests pass a client bound to a mock server. The public constructor sets a request factory, which would replace
     * the mock.
     */
    SlackFeedback(String url, RestClient client) {
        this.client = client;
        this.webhook = webhookOrNull(url);
        if (webhook != null) {
            LOG.info("Feedback is on: FEEDBACK_SLACK_WEBHOOK_URL is set");
        } else if (url.isBlank()) {
            LOG.info("Feedback is off: FEEDBACK_SLACK_WEBHOOK_URL is not set");
        } else {
            LOG.warn("Feedback is off: FEEDBACK_SLACK_WEBHOOK_URL is not an https URL with a host");
        }
    }

    private static URI webhookOrNull(String url) {
        try {
            var uri = URI.create(url.strip());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
        } catch (IllegalArgumentException e) {
            // not logged, because the message contains the URL
            return null;
        }
    }

    /** Returns false when the webhook URL is empty or invalid. */
    public boolean isOn() {
        return webhook != null;
    }

    /**
     * Posts the feedback and returns when Slack answers {@code ok}. Call it only when {@link #isOn()} returns true.
     *
     * @param email the visitor's email, or {@code null}
     * @param page the page address without the scheme, for example {@code ai.demo.vaadin.com/dashboard}
     * @throws FeedbackNotSentException when the feedback was not posted
     */
    public void send(String text, String email, String page, CurrentUser.SignedInUser demoUser) {
        var escapedMessage = escaped(message(text, email, page, demoUser));
        var failureForLog = post(escapedMessage);
        if (failureForLog != null) {
            // Never log the text, the email or the URL.
            LOG.warn("Feedback not sent: {}", failureForLog);
            throw new FeedbackNotSentException();
        }
        LOG.info("Feedback sent");
    }

    private static String message(String text, String email, String page, CurrentUser.SignedInUser demoUser) {
        // The email and the page come from the visitor. A line break in them could add a fake label.
        var labels = String.join(
                "\n",
                FIRST_LINE,
                "Email: " + (email == null || email.isBlank() ? "not given" : oneLine(email)),
                "Page: " + oneLine(page),
                "User: " + demoUser.name() + ", " + demoUser.scope().label());
        // Each line quoted, so that a line that imitates a label shows inside the quote. \R first, because lines()
        // does not split on U+2028, U+2029 and U+0085.
        var quoted =
                text.replaceAll("\\R", "\n").lines().map(line -> "> " + line).collect(Collectors.joining("\n"));
        return labels + "\n\n" + quoted;
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\R", " ");
    }

    private static String escaped(String message) {
        // Slack decodes only these three. Escaping stops mentions such as <!here> and links that hide their address.
        // Slack still shows &gt; at a line start as a quote. & first, so that the entities for < and > stay intact.
        return message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Returns {@code null} on success, otherwise the failure for the log line. */
    private String post(String escapedMessage) {
        try {
            var answer = client.post()
                    .uri(webhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    // A map, so that the visitor's text cannot break the JSON or add a field. The unfurl fields stop
                    // previews of a visitor's links. Do not add link_names, because Slack then turns @here into a
                    // mention.
                    .body(Map.of("text", escapedMessage, "unfurl_links", false, "unfurl_media", false))
                    .retrieve()
                    // Slack's only success is the answer ok. Do not use toBodilessEntity(), which accepts any 2xx
                    // answer.
                    .toEntity(String.class);
            return "ok".equals(answer.getBody())
                    ? null
                    : answerForLog(answer.getStatusCode().value(), answer.getBody());
        } catch (RestClientResponseException e) {
            return answerForLog(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            // The class of the root cause only, never a message, which can contain the URL. The class tells an
            // unknown host, a refused connection and a timeout apart.
            return "error="
                    + NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
        }
    }

    private static String answerForLog(int status, String body) {
        // A Slack error code as it is. Any other body only by its length, so that an error page or a line break does
        // not reach the log.
        var bodyForLog = body != null && SLACK_ERROR.matcher(body).matches()
                ? "body=" + body
                : "body.length=" + (body == null ? 0 : body.length());
        return "status=" + status + " " + bodyForLog;
    }
}
