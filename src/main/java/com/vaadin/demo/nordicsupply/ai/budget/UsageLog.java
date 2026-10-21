package com.vaadin.demo.nordicsupply.ai.budget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import com.vaadin.flow.component.ai.common.AIAttachment;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener.ResponseEvent;
import com.vaadin.flow.component.ai.provider.ResponseMetadata;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * How visitors use the public demo, for the team to look back on without the running application: one event per
 * line, as JSON, in a file of its own for each day, such as {@code usage-2026-10-20.jsonl}, in the directory that
 * {@code app.usage.dir} names. Neither the database, which starts over with the instance, nor the application log.
 * Without a directory nothing is written, as on a developer's machine. A file is deleted when it is {@value #DAYS_KEPT}
 * days old, as the help of the Dashboard and the Claims view says.
 * <p>
 * Spring Boot's structured logging writes the lines in its Logstash format: beside Logstash's own fields, an event has
 * its time in {@code @timestamp}, its kind in {@code message}, and the fields of its kind:
 * <ul>
 * <li>{@code visit}, a visit started with its session's first page: {@code visit}.
 * <li>{@code view}, a view the visitor opened: {@code visit} and {@code view}, the view's route.
 * <li>{@code turn}, an AI turn ended: {@code visit}, {@code feature}, {@code prompt}, {@code attachments},
 *     {@code outcome}, {@code finish_reason}, {@code input_tokens}, {@code output_tokens}, {@code duration_ms},
 *     {@code model}, and for a failure {@code error_class} and {@code error_message}. The outcome is {@code answered},
 *     {@code failed}, {@code abandoned} for a turn that never ran, or {@code let go} for one still open at the budget's
 *     deadline; should the response of a turn let go come after all, it is a second event, {@code answered late} or
 *     {@code failed late}.
 * <li>{@code refusal}, a request refused before it reached the model: {@code visit}, {@code feature},
 *     {@code prompt}, {@code attachments}, and {@code cap}, the property under {@code app.ai} that refused it.
 * </ul>
 * The visit is the random id of the session's {@code Visit}, never the session's own id; it tells the sessions apart
 * and nothing names the visitor, not even the address the budget counts by. The feature is {@code insights} or
 * {@code claims}. A prompt is kept in full; a refused one up to its first 500 characters, as the activity log keeps
 * it, which by default cuts only a question refused for its length. Of an attachment only its {@code name} and its
 * size in {@code bytes} are kept, never its contents.
 */
@Component
public class UsageLog {

    /** How many days a day's file is kept before it is deleted. */
    public static final int DAYS_KEPT = 90;

    /** As much of a refused prompt as is kept: what the activity log keeps, and a question's cap by default. */
    static final int KEPT_OF_A_REFUSED_PROMPT = 500;

    private static final String VISIT = "visit";

    private static final Logger LOG = LoggerFactory.getLogger(UsageLog.class);

    /** Where the events go: the day's file, or nowhere. */
    private final Logger events;

    /** The logging that writes the files, apart from the application's own; {@code null} while nothing is written. */
    private final LoggerContext files;

    /** The model every turn goes to, as {@code application.properties} names it. */
    private final String model;

    /**
     * @param dir the directory of the files; nothing is written without one
     * @param model the model every turn goes to
     * @param environment the settings of Spring Boot's structured logging, as the application's own log reads them
     */
    public UsageLog(
            @Value("${app.usage.dir:}") String dir,
            @Value("${spring.ai.openai.chat.model}") String model,
            Environment environment) {
        this.model = model;
        if (dir == null || dir.isBlank()) {
            files = null;
            events = NOPLogger.NOP_LOGGER;
        } else {
            files = new LoggerContext();
            events = writingTo(Path.of(dir), files, environment);
        }
    }

    /**
     * A logger apart from the application's, so that no event reaches the application log and no setting of that log
     * changes what is written here. It starts a file each day, named by the day, and deletes the files older than
     * {@link #DAYS_KEPT} days at start and as each day ends. A directory it cannot write to stops the application's
     * start, and a failure to write later, such as a full disk, is a warning in the application's log.
     */
    private static Logger writingTo(Path dir, LoggerContext context, Environment environment) {
        // what Spring Boot and SLF4J give the application's context: the encoder reads Spring's environment, and every
        // event the context's MDC, which nothing here writes to
        context.putObject(Environment.class.getName(), environment);
        context.setMDCAdapter(new LogbackMDCAdapter());
        // logback tells a failure only to the context it happened in
        StatusListener failures = status -> {
            if (status.getLevel() == Status.ERROR) {
                LOG.warn("The usage log failed: {}", status.getMessage(), status.getThrowable());
            }
        };
        context.getStatusManager().add(failures);
        var appender = new RollingFileAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName("usage");
        var days = new TimeBasedRollingPolicy<ILoggingEvent>();
        days.setContext(context);
        days.setParent(appender);
        days.setFileNamePattern(
                dir.toAbsolutePath().resolve("usage-%d{yyyy-MM-dd}.jsonl").toString());
        days.setMaxHistory(DAYS_KEPT);
        days.setCleanHistoryOnStart(true);
        days.start();
        var json = new StructuredLogEncoder();
        json.setContext(context);
        json.setFormat("logstash");
        json.start();
        appender.setRollingPolicy(days);
        appender.setEncoder(json);
        appender.start();
        if (!appender.isStarted()) {
            var error = context.getStatusManager().getCopyOfStatusList().stream()
                    .filter(status -> status.getLevel() == Status.ERROR)
                    .findFirst();
            context.stop();
            throw new IllegalStateException(
                    "The usage log cannot write to " + dir
                            + error.map(status -> ": " + status.getMessage()).orElse(""),
                    error.map(Status::getThrowable).orElse(null));
        }
        var logger = context.getLogger("usage");
        logger.addAppender(appender);
        return logger;
    }

    /**
     * A visit has started, with its session's first page.
     *
     * @param visit the visit
     */
    public void visitStarted(UUID visit) {
        events.atInfo().setMessage(VISIT).addKeyValue(VISIT, visit).log();
    }

    /**
     * The visitor has opened a view.
     *
     * @param visit the visit
     * @param view the view's route
     */
    public void viewOpened(UUID visit, String view) {
        events.atInfo()
                .setMessage("view")
                .addKeyValue(VISIT, visit)
                .addKeyValue("view", view)
                .log();
    }

    /**
     * A turn has ended, however it ended.
     *
     * @param request what was asked, and by whom
     * @param outcome how the turn ended, as the budget names it
     * @param ended the response, or {@code null} for a turn that never ran or was let go
     * @param took how long the turn took
     */
    void turnEnded(Request request, String outcome, ResponseEvent ended, Duration took) {
        var metadata = Optional.ofNullable(ended).flatMap(ResponseEvent::getMetadata);
        var tokens = metadata.map(ResponseMetadata::tokenUsage);
        var error = Optional.ofNullable(ended).flatMap(ResponseEvent::getError);
        about("turn", request, request.prompt())
                .addKeyValue("outcome", outcome)
                .addKeyValue(
                        "finish_reason",
                        metadata.map(ResponseMetadata::finishReason).orElse(null))
                .addKeyValue(
                        "input_tokens",
                        tokens.map(ResponseMetadata.TokenUsage::inputTokens).orElse(null))
                .addKeyValue(
                        "output_tokens",
                        tokens.map(ResponseMetadata.TokenUsage::outputTokens).orElse(null))
                .addKeyValue("duration_ms", took.toMillis())
                .addKeyValue("model", model)
                .addKeyValue(
                        "error_class", error.map(e -> e.getClass().getName()).orElse(null))
                .addKeyValue("error_message", error.map(Throwable::getMessage).orElse(null))
                .log();
    }

    /**
     * A request refused before it reached the model.
     *
     * @param request what was asked, and by whom
     * @param cap the cap that refused it, named by its property under {@code app.ai}
     */
    void refused(Request request, String cap) {
        about("refusal", request, kept(request.prompt()))
                .addKeyValue("cap", cap)
                .log();
    }

    /** An event about a request: in which visit, from which feature, and what, attachments by name and size alone. */
    private LoggingEventBuilder about(String event, Request request, String prompt) {
        return events.atInfo()
                .setMessage(event)
                .addKeyValue(VISIT, request.caller().visit())
                .addKeyValue("feature", request.feature())
                .addKeyValue("prompt", prompt)
                .addKeyValue(
                        "attachments",
                        request.attachments().stream().map(UsageLog::named).toList());
    }

    /** An attachment as the log keeps it: its name and its size, never what it holds. */
    private static Map<String, Object> named(AIAttachment attachment) {
        var named = new LinkedHashMap<String, Object>();
        named.put("name", attachment.name());
        named.put("bytes", attachment.data().length);
        return named;
    }

    /** A refused prompt up to its first 500 characters, cut where a character begins. */
    private static String kept(String prompt) {
        if (prompt == null || prompt.codePointCount(0, prompt.length()) <= KEPT_OF_A_REFUSED_PROMPT) {
            return prompt;
        }
        return prompt.substring(0, prompt.offsetByCodePoints(0, KEPT_OF_A_REFUSED_PROMPT)) + "…";
    }

    /** Closes the day's file as the application stops, and waits for the deletion of old files to finish. */
    @PreDestroy
    public void close() {
        if (files != null) {
            files.stop();
        }
    }
}
