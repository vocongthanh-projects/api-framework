package vn.ots.qc.api.nosql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;

/**
 * Utility to load NoSQL query files (e.g. .json or .nosql) from classpath and replace dynamic
 * placeholders (${key} or {{key}}).
 */
public final class NoSqlQueryLoader {
  private static final Pattern PARAM_PATTERN = Pattern.compile("(\\$\\{|\\{\\{)(.*?)(}\\}|})");

  private NoSqlQueryLoader() {}

  /** Reads a query resource from classpath into raw string. */
  public static String loadRaw(String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    try (InputStream stream = NoSqlQueryLoader.class.getResourceAsStream(normalized)) {
      if (stream == null) {
        throw new IllegalArgumentException(
            "NoSQL resource file not found on classpath: " + normalized);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read NoSQL resource file: " + normalized, e);
    }
  }

  /** Reads a query resource and substitutes parameters formatted as ${name} or {{name}}. */
  public static String loadQuery(String resourcePath, Map<String, ?> parameters) {
    String template = loadRaw(resourcePath);
    Map<String, ?> values = parameters == null ? Map.of() : parameters;
    Matcher matcher = PARAM_PATTERN.matcher(template);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(2).trim();
      if (!values.containsKey(key)) {
        throw new IllegalArgumentException("Missing NoSQL query parameter: " + key);
      }
      Object value = values.get(key);
      String replacement;
      if (insideJsonString(template, matcher.start())) {
        replacement = escapeJsonString(value == null ? "null" : String.valueOf(value));
      } else if (value == null || value instanceof Number || value instanceof Boolean) {
        replacement = value == null ? "null" : String.valueOf(value);
      } else {
        throw new IllegalArgumentException(
            "Unquoted NoSQL placeholders only support numbers, booleans, or null: " + key);
      }
      replacement = Matcher.quoteReplacement(replacement);
      matcher.appendReplacement(sb, replacement);
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static boolean insideJsonString(String json, int index) {
    boolean quoted = false;
    boolean escaped = false;
    for (int i = 0; i < index; i++) {
      char c = json.charAt(i);
      if (escaped) escaped = false;
      else if (c == '\\' && quoted) escaped = true;
      else if (c == '"') quoted = !quoted;
    }
    return quoted;
  }

  private static String escapeJsonString(String value) {
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
          else escaped.append(c);
        }
      }
    }
    return escaped.toString();
  }

  /** Parses a JSON string to BSON Document. */
  public static Document toDocument(String json) {
    Objects.requireNonNull(json, "json string must not be null");
    return Document.parse(json);
  }

  /** Loads a query resource with parameter substitution and parses to BSON Document. */
  public static Document loadDocument(String resourcePath, Map<String, ?> parameters) {
    String json = loadQuery(resourcePath, parameters);
    return toDocument(json);
  }
}
