package com.vaadin.demo.nordicsupply.ai.budget;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every event is one line of JSON in the day's file, with the fields the team reads back: the visit, what was asked,
 * and what became of it, never the caller's address or what an attachment holds. A day's file goes after ninety
 * days, and without a directory nothing is written.
 */
class UsageLogTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final UUID VISIT = UUID.randomUUID();

    private static final Caller CALLER = new Caller(VISIT, "2001:db8:1:2:aaaa::1");

    @TempDir
    Path dir;

    private UsageLog usage;

    @AfterEach
    void closed() {
        if (usage != null) {
            // the file has to be closed before the directory can go, on Windows
            usage.close();
        }
    }

    private UsageLog writing() {
        usage = new UsageLog(dir.toString(), "gpt-test", new StandardEnvironment());
        return usage;
    }

    /** The events written so far, closing the file first. */
    private List<Map<String, Object>> events() throws IOException {
        usage.close();
        var lines = Files.readAllLines(dir.resolve("usage-" + LocalDate.now() + ".jsonl"), UTF_8);
        return lines.stream()
                .map(line -> JSON.readValue(line, new TypeReference<Map<String, Object>>() {}))
                .toList();
    }

    @Test
    void aVisitAndTheViewsItOpensAreEvents() throws IOException {
        var usage = writing();

        usage.visitStarted(VISIT);
        usage.viewOpened(VISIT, "/dashboard");

        var events = events();
        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .containsKey("@timestamp")
                .containsEntry("message", "visit")
                .containsEntry("visit", VISIT.toString())
                .doesNotContainKey("address");
        assertThat(events.get(1))
                .containsEntry("message", "view")
                .containsEntry("visit", VISIT.toString())
                .containsEntry("view", "/dashboard");
    }

    @Test
    void anAnsweredTurnKeepsItsPromptTokensAndDuration() throws IOException {
        var usage = writing();
        var answered = new ResponseEvent(
                "Done.", null, new ResponseMetadata("STOP", new ResponseMetadata.TokenUsage(30_000, 400, 30_400)));

        usage.turnEnded(
                new Request(CALLER, "insights", "Orders per month this year", List.of()),
                "answered",
                answered,
                Duration.ofMillis(5300));

        assertThat(events().get(0))
                .containsEntry("message", "turn")
                .containsEntry("visit", VISIT.toString())
                .doesNotContainKey("address")
                .containsEntry("feature", "insights")
                .containsEntry("prompt", "Orders per month this year")
                .containsEntry("attachments", List.of())
                .containsEntry("outcome", "answered")
                .containsEntry("finish_reason", "STOP")
                .containsEntry("input_tokens", 30_000)
                .containsEntry("output_tokens", 400)
                .containsEntry("duration_ms", 5300)
                .containsEntry("model", "gpt-test")
                .containsEntry("error_class", null)
                .containsEntry("error_message", null);
    }

    /** A failure keeps its exception's class and message; an attachment only its name and size. */
    @Test
    void aFailedTurnKeepsItsExceptionAndItsAttachmentsByNameAndSize() throws IOException {
        var usage = writing();
        var message = new AIAttachment("Order 17.eml", "text/plain", "Three cartons missing.".getBytes(UTF_8));

        usage.turnEnded(
                new Request(CALLER, "claims", "Fill in the claim form.", List.of(message)),
                "failed",
                new ResponseEvent(null, new IllegalStateException("The model is down"), null),
                Duration.ofSeconds(2));
        usage.turnEnded(
                new Request(CALLER, "claims", "Fill in the claim form.", List.of(message)),
                "let go",
                null,
                Duration.ofMinutes(45));

        var events = events();
        assertThat(events.get(0))
                .containsEntry("outcome", "failed")
                .containsEntry("attachments", List.of(Map.of("name", "Order 17.eml", "bytes", 22)))
                .containsEntry("finish_reason", null)
                .containsEntry("input_tokens", null)
                .containsEntry("error_class", "java.lang.IllegalStateException")
                .containsEntry("error_message", "The model is down");
        assertThat(events.get(1))
                .containsEntry("outcome", "let go")
                .containsEntry("duration_ms", 2_700_000)
                .containsEntry("error_class", null);
        assertThat(Files.readString(dir.resolve("usage-" + LocalDate.now() + ".jsonl")))
                .doesNotContain("cartons");
    }

    /** A refusal names its cap, and keeps a long prompt's first 500 characters, as the activity log does. */
    @Test
    void aRefusalNamesItsCapAndKeepsTheFirst500CharactersOfAPrompt() throws IOException {
        var usage = writing();

        usage.refused(new Request(CALLER, "insights", "x".repeat(800), List.of()), "max-question-length");
        usage.refused(new Request(CALLER, "insights", "Open claims by type", List.of()), "address.turns-per-day");
        usage.refused(
                new Request(
                        CALLER, "claims", null, List.of(new AIAttachment("scan.pdf", "application/pdf", new byte[12]))),
                "max-file-size");

        var events = events();
        assertThat(events.get(0))
                .containsEntry("message", "refusal")
                .containsEntry("visit", VISIT.toString())
                .doesNotContainKey("address")
                .containsEntry("feature", "insights")
                .containsEntry("cap", "max-question-length")
                .containsEntry("prompt", "x".repeat(500) + "…");
        assertThat(events.get(1))
                .containsEntry("cap", "address.turns-per-day")
                .containsEntry("prompt", "Open claims by type");
        assertThat(events.get(2))
                .containsEntry("cap", "max-file-size")
                .containsEntry("prompt", null)
                .containsEntry("attachments", List.of(Map.of("name", "scan.pdf", "bytes", 12)));
    }

    @Test
    void aFileOlderThanNinetyDaysIsDeletedAndANewerOneKept() throws IOException {
        var old = Files.writeString(dir.resolve("usage-" + LocalDate.now().minusDays(100) + ".jsonl"), "{}\n");
        var recent = Files.writeString(dir.resolve("usage-" + LocalDate.now().minusDays(10) + ".jsonl"), "{}\n");

        // the deletion runs in the background as the log starts, and closing waits for it
        writing().close();

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
    }

    @Test
    void withoutADirectoryNothingIsWritten() {
        var off = new UsageLog("", "gpt-test", null);

        assertThatCode(() -> {
                    off.visitStarted(VISIT);
                    off.refused(new Request(CALLER, "insights", "Open claims by type", List.of()), "enabled");
                    off.close();
                })
                .doesNotThrowAnyException();
    }

    /** A directory that cannot be written stops the start, rather than losing every event. */
    @Test
    void aDirectoryThatCannotBeWrittenStopsTheStart() throws IOException {
        var file = Files.writeString(dir.resolve("not-a-directory"), "");

        assertThatThrownBy(() -> new UsageLog(file.resolve("usage").toString(), "gpt-test", new StandardEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("The usage log cannot write to " + file.resolve("usage"));
    }
}
