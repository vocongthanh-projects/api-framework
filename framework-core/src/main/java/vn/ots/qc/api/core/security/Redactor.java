package vn.ots.qc.api.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Redacts structured and unstructured secrets before data reaches logs or reports. */
public final class Redactor {
  public static final String MASK = "<redacted>";
  private static final int MAX_BODY_LENGTH = 16_384;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern BEARER =
      Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+", Pattern.UNICODE_CASE);

  private final Set<String> sensitiveNames;
  private final Pattern assignment;

  public Redactor() {
    this(
        Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "password",
            "token",
            "access_token",
            "refresh_token",
            "client_secret",
            "otp",
            "authorizationcode",
            "authorization_code"));
  }

  public Redactor(Set<String> sensitiveNames) {
    this.sensitiveNames =
        sensitiveNames.stream()
            .map(this::normalize)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    String names =
        sensitiveNames.stream()
            .map(Pattern::quote)
            .collect(java.util.stream.Collectors.joining("|"));
    assignment =
        Pattern.compile(
            "(?i)([\\\"']?(?:"
                + names
                + ")[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\\"'&,;\\s}]+)([\\\"']?)");
  }

  public Map<String, String> headers(Map<String, String> headers) {
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach((key, value) -> result.put(key, sensitive(key) ? MASK : text(value)));
    return Map.copyOf(result);
  }

  public String body(String body) {
    if (body == null || body.isBlank()) {
      return body == null ? "" : body;
    }
    try {
      JsonNode root = MAPPER.readTree(body);
      redactNode(root);
      return truncate(MAPPER.writeValueAsString(root));
    } catch (Exception ignored) {
      return text(body);
    }
  }

  public String text(String value) {
    if (value == null || value.isBlank()) {
      return value == null ? "" : value;
    }
    String redacted = BEARER.matcher(value).replaceAll("Bearer " + MASK);
    redacted = assignment.matcher(redacted).replaceAll("$1" + MASK + "$3");
    return truncate(redacted);
  }

  private void redactNode(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object
          .fieldNames()
          .forEachRemaining(
              name -> {
                if (sensitive(name)) {
                  object.put(name, MASK);
                } else {
                  redactNode(object.get(name));
                }
              });
    } else if (node.isArray()) {
      node.forEach(this::redactNode);
    }
  }

  private String truncate(String value) {
    return value.length() > MAX_BODY_LENGTH
        ? value.substring(0, MAX_BODY_LENGTH) + "...[truncated]"
        : value;
  }

  private boolean sensitive(String name) {
    return sensitiveNames.contains(normalize(name));
  }

  private String normalize(String name) {
    return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
  }
}
