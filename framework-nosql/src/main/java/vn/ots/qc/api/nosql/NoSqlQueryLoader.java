package vn.ots.qc.api.nosql;

import org.bson.Document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to load NoSQL query files (e.g. .json or .nosql) from classpath
 * and replace dynamic placeholders (${key} or {{key}}).
 */
public final class NoSqlQueryLoader {
    private static final Pattern PARAM_PATTERN = Pattern.compile("(\\$\\{|\\{\\{)(.*?)(}\\}|})");

    private NoSqlQueryLoader() {}

    /**
     * Reads a query resource from classpath into raw string.
     */
    public static String loadRaw(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream stream = NoSqlQueryLoader.class.getResourceAsStream(normalized)) {
            if (stream == null) {
                throw new IllegalArgumentException("NoSQL resource file not found on classpath: " + normalized);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read NoSQL resource file: " + normalized, e);
        }
    }

    /**
     * Reads a query resource and substitutes parameters formatted as ${name} or {{name}}.
     */
    public static String loadQuery(String resourcePath, Map<String, ?> parameters) {
        String template = loadRaw(resourcePath);
        if (parameters == null || parameters.isEmpty()) {
            return template;
        }

        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(2).trim();
            Object value = parameters.get(key);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Parses a JSON string to BSON Document.
     */
    public static Document toDocument(String json) {
        Objects.requireNonNull(json, "json string must not be null");
        return Document.parse(json);
    }

    /**
     * Loads a query resource with parameter substitution and parses to BSON Document.
     */
    public static Document loadDocument(String resourcePath, Map<String, ?> parameters) {
        String json = loadQuery(resourcePath, parameters);
        return toDocument(json);
    }
}
