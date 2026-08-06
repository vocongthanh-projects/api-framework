package vn.ots.qc.api.core.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ApiResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final Duration duration;
    private final int attempts;

    public ApiResponse(int statusCode, Map<String, List<String>> headers, String body,
                       Duration duration, int attempts) {
        this(statusCode, headers, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), duration, attempts);
    }

    public ApiResponse(int statusCode, Map<String, List<String>> headers, byte[] body,
                       Duration duration, int attempts) {
        this.statusCode = statusCode;
        this.headers = Map.copyOf(headers == null ? Map.of() : headers);
        this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        this.duration = duration == null ? Duration.ZERO : duration;
        this.attempts = attempts;
    }

    public int statusCode() { return statusCode; }
    public Map<String, List<String>> headers() { return headers; }
    public String body() { return new String(body, StandardCharsets.UTF_8); }
    public byte[] bytes() { return Arrays.copyOf(body, body.length); }
    public Duration duration() { return duration; }
    public int attempts() { return attempts; }
    public boolean isSuccessful() { return statusCode >= 200 && statusCode < 300; }

    public <T> T as(Class<T> type) {
        try { return MAPPER.readValue(body, type); }
        catch (IOException e) { throw new IllegalStateException("Cannot deserialize response body", e); }
    }

    public JsonNode json() {
        try { return MAPPER.readTree(body); }
        catch (IOException e) { throw new IllegalStateException("Response is not valid JSON", e); }
    }

    public ApiResponse requireStatus(int expected) {
        if (statusCode != expected) {
            throw new AssertionError("Expected HTTP " + expected + " but got " + statusCode
                    + ". Inspect the redacted exchange trace for response details.");
        }
        return this;
    }

    public Path saveTo(Path destination) {
        if (destination == null) throw new IllegalArgumentException("destination is required");
        Path normalized = destination.toAbsolutePath().normalize();
        if (Files.exists(normalized) && Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("destination must be a file: " + normalized);
        }
        try {
            Path parent = normalized.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(normalized, body);
            return normalized;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save response to " + normalized, exception);
        }
    }
}
