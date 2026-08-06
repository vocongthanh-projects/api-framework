package vn.ots.qc.api.testng;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.core.security.Redactor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Temporary machine-readable trace output until a consumer-driven Allure sink is implemented. */
final class TraceReportWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path outputDirectory;
    private final Redactor redactor = new Redactor();

    TraceReportWriter(Path outputDirectory) { this.outputDirectory = outputDirectory; }

    Path write(TestContext context, String status, Throwable error) {
        try {
            Files.createDirectories(outputDirectory);
            Path file = outputDirectory.resolve(safe(context.testId()) + ".json");
            Instant finishedAt = Instant.now();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("testId", context.testId());
            report.put("status", status);
            report.put("startedAt", context.startedAt().toString());
            report.put("finishedAt", finishedAt.toString());
            report.put("durationMillis", Duration.between(context.startedAt(), finishedAt).toMillis());
            report.put("actors", context.actors().keySet());
            report.put("error", error == null ? null : redactor.text(
                    error.getClass().getSimpleName() + ": " + error.getMessage()));
            report.put("exchanges", context.traces().stream().map(this::exchange).toList());
            try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                MAPPER.writeValue(output, report);
            }
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write API trace report", e);
        }
    }

    private Map<String, Object> exchange(ExchangeTrace trace) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("timestamp", trace.timestamp().toString());
        value.put("service", trace.service());
        value.put("actor", trace.actor());
        value.put("method", trace.method());
        value.put("path", trace.path());
        value.put("attempt", trace.attempt());
        value.put("statusCode", trace.statusCode());
        value.put("durationMillis", trace.durationMillis());
        value.put("requestHeaders", trace.requestHeaders());
        value.put("requestBody", trace.requestBody());
        value.put("responseBody", trace.responseBody());
        value.put("error", trace.error());
        return value;
    }

    private String safe(String value) {
        String safe = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.length() > 180 ? safe.substring(0, 180) : safe;
    }
}
