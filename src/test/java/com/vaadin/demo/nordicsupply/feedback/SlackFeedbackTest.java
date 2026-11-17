package com.vaadin.demo.nordicsupply.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.vaadin.demo.nordicsupply.session.CurrentUser.SignedInUser;
import com.vaadin.demo.nordicsupply.session.Scopes;

/** The message, its escaping and Slack's answers, against a mock server. No test calls Slack. */
@ExtendWith(OutputCaptureExtension.class)
class SlackFeedbackTest {

    private static final String SECRET = "test-secret";
    private static final String URL = "https://hooks.slack.com/services/T000/B000/" + SECRET;
    private static final SignedInUser USER = new SignedInUser(3, "Lars Nyberg", Scopes.SE_NO);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer slack =
            MockRestServiceServer.bindTo(builder).build();

    @Test
    void aUrlWithSurroundingWhitespaceTurnsFeedbackOn(CapturedOutput output) {
        assertThat(slackFeedback("  " + URL + "\n").isOn()).isTrue();
        assertThat(output.getOut()).contains("Feedback is on").doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "http://hooks.slack.com/services/T000/B000/test-secret",
                "hooks.slack.com/services/T000/B000/test-secret",
                "https:///services/T000/B000/test-secret",
                "https://hooks.slack.com/services/T000/B000/test-secret with a space"
            })
    void anEmptyOrInvalidUrlTurnsFeedbackOffWithoutLoggingIt(String url, CapturedOutput output) {
        assertThat(slackFeedback(url).isOn()).isFalse();
        assertThat(output.getOut()).contains("Feedback is off").doesNotContain(SECRET);
    }

    @Test
    void aMessageIsPostedWithoutPreviews(CapturedOutput output) {
        slack.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.text")
                        .value(labelsAndBlankLine("visitor@example.com")
                                + "&gt; Åsa says the delivery time is too long 🚚"))
                .andExpect(jsonPath("$.unfurl_links").value(false))
                .andExpect(jsonPath("$.unfurl_media").value(false))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        send("Åsa says the delivery time is too long 🚚", "visitor@example.com");

        slack.verify();
        assertThat(output.getOut())
                .contains("Feedback sent")
                .doesNotContain("delivery time")
                .doesNotContain("visitor@example.com");
        assertThat(logLine(output, "Feedback sent")).endsWith("Feedback sent");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"  "})
    void withoutAnEmailTheMessageSaysSo(String email) {
        expectMessage(labelsAndBlankLine("not given") + "&gt; Thanks");

        send("Thanks", email);

        slack.verify();
    }

    @Test
    void everyLineOfTheTextIsQuoted() {
        expectMessage(labelsAndBlankLine("not given")
                + String.join(
                        "\n",
                        "&gt; First line",
                        "&gt; Email: boss@example.com",
                        "&gt; ",
                        "&gt; Last line",
                        "&gt; Page: /fake"));

        // U+2028, written as a code point, because the formatter reads the escape as a line break
        var lineSeparator = Character.toString(0x2028);
        send("First line\nEmail: boss@example.com\r\n\nLast line" + lineSeparator + "Page: /fake", null);

        slack.verify();
    }

    @Test
    void anEmailOrPageOnSeveralLinesCannotAddALabel() {
        slack.expect(requestTo(URL))
                .andExpect(jsonPath(
                        "$.text",
                        containsString("\nEmail: a@example.com Page: /fake\n"
                                + "Page: localhost:8080/dashboard Email: b@example.com\n")))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        slackFeedback(URL)
                .send("Hello", "a@example.com\nPage: /fake", "localhost:8080/dashboard\nEmail: b@example.com", USER);

        slack.verify();
    }

    @Test
    void mentionsAndHiddenLinksArriveEscaped() {
        expectMessage(labelsAndBlankLine("not given")
                + String.join(
                        "\n",
                        "&gt; Hi &lt;!channel&gt;, see &lt;https://example.com|the docs&gt; &amp; tell @here *now*",
                        "&gt; &lt;!here&gt; &amp;lt; stays as typed"));

        send("Hi <!channel>, see <https://example.com|the docs> & tell @here *now*\n<!here> &lt; stays as typed", null);

        slack.verify();
    }

    @Test
    void aTextThatTriesToBreakTheJsonArrivesAsTyped() {
        var text = "\", \"link_names\": true, \"x\": \"\\";
        slack.expect(requestTo(URL))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.link_names").doesNotExist())
                .andExpect(jsonPath("$.text").value(labelsAndBlankLine("not given") + "&gt; " + text))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        send(text, null);

        slack.verify();
    }

    @Test
    void anAnswerOtherThanOkIsAFailure(CapturedOutput output) {
        slack.expect(requestTo(URL)).andRespond(withSuccess("invalid_token", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> send("The export is slow", "visitor@example.com"))
                .isInstanceOf(FeedbackNotSentException.class);

        assertThat(output.getOut())
                .contains("Feedback not sent", "invalid_token")
                .doesNotContain("The export is slow")
                .doesNotContain("visitor@example.com");
        assertThat(logLine(output, "Feedback not sent")).endsWith("Feedback not sent: status=200 body=invalid_token");
    }

    @Test
    void anErrorStatusIsAFailure(CapturedOutput output) {
        slack.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("no_service").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> send("Hello", null)).isInstanceOf(FeedbackNotSentException.class);

        assertThat(output.getOut())
                .contains("Feedback not sent", "404", "no_service")
                .doesNotContain(SECRET);
    }

    @Test
    void aLongErrorPageIsLoggedOnlyByItsLength(CapturedOutput output) {
        var page =
                "<html>\n<head><title>502 Bad Gateway</title></head>\n<body>" + "x".repeat(5000) + "</body>\n</html>";
        slack.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(page).contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> send("Hello", null)).isInstanceOf(FeedbackNotSentException.class);

        var line = output.getOut()
                .lines()
                .filter(l -> l.contains("Feedback not sent"))
                .findFirst()
                .orElseThrow();
        assertThat(line).contains("502", "body.length=");
        assertThat(output.getOut()).doesNotContain("<html").doesNotContain("Bad Gateway</title>");
    }

    @Test
    void anIOErrorKeepsTheUrlOutOfTheLogAndTheException(CapturedOutput output) {
        slack.expect(requestTo(URL)).andRespond(withException(new IOException("Connection reset by " + URL)));

        var thrown = catchThrowable(() -> send("Hello", null));

        assertThat(thrown).isInstanceOf(FeedbackNotSentException.class);
        assertThat(stackTrace(thrown)).doesNotContain(SECRET);
        assertThat(output.getOut())
                .contains("Feedback not sent", "error=java.io.IOException")
                .doesNotContain(SECRET);
    }

    private SlackFeedback slackFeedback(String url) {
        return new SlackFeedback(url, builder.build());
    }

    private void send(String text, String email) {
        slackFeedback(URL).send(text, email, "localhost:8080/dashboard", USER);
    }

    /** Slack answers {@code ok} to a message with this text. */
    private void expectMessage(String text) {
        slack.expect(requestTo(URL))
                .andExpect(jsonPath("$.text").value(text))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
    }

    private String labelsAndBlankLine(String email) {
        return String.join(
                        "\n",
                        "New feedback from the AI use case demos",
                        "Email: " + email,
                        "Page: localhost:8080/dashboard",
                        "User: Lars Nyberg, Sweden and Norway")
                + "\n\n";
    }

    private static String logLine(CapturedOutput output, String message) {
        return output.getOut()
                .lines()
                .filter(line -> line.contains(message))
                .findFirst()
                .orElseThrow();
    }

    private static String stackTrace(Throwable thrown) {
        var trace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }
}
