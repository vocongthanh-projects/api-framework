package vn.ots.qc.consumer.template.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.AuthProvider;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.consumer.template.config.ConsumerConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** Consumer-side examples for static credentials and common JSON login endpoints. */
public final class ConfiguredAuthProvider implements AuthProvider {
    public enum Mode { BEARER_TOKEN, COOKIE, JSON_BEARER_LOGIN, JSON_COOKIE_LOGIN }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ConsumerConfig config;
    private final Mode mode;
    private final HttpClient http;

    public ConfiguredAuthProvider(ConsumerConfig config) {
        this.config = config;
        String configuredMode = config.require("auth.mode").toUpperCase(Locale.ROOT).replace('-', '_');
        this.mode = Mode.valueOf(configuredMode);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public AuthMaterial authenticate(Actor actor) {
        return switch (mode) {
            case BEARER_TOKEN -> AuthMaterial.bearer(config.require("auth.token"), null);
            case COOKIE -> new AuthMaterial(Map.of(), Map.of(
                    config.require("auth.cookie-name"), config.require("auth.cookie-value")), null);
            case JSON_BEARER_LOGIN -> jsonBearerLogin();
            case JSON_COOKIE_LOGIN -> jsonCookieLogin();
        };
    }

    private AuthMaterial jsonBearerLogin() {
        JsonNode response = login();
        String token = requiredText(response.at(config.get("auth.token-json-pointer", "/access_token")),
                "auth token response field");
        String expiresPointer = config.get("auth.expires-in-json-pointer", "/expires_in");
        JsonNode expires = response.at(expiresPointer);
        Instant expiresAt = expires.isNumber() ? Instant.now().plusSeconds(expires.asLong()) : null;
        return AuthMaterial.bearer(token, expiresAt);
    }

    private AuthMaterial jsonCookieLogin() {
        HttpResponse<String> response = sendLogin();
        String wantedName = config.require("auth.cookie-name");
        for (String setCookie : response.headers().allValues("set-cookie")) {
            for (java.net.HttpCookie cookie : java.net.HttpCookie.parse(setCookie)) {
                if (cookie.getName().equals(wantedName)) {
                    Instant expiresAt = cookie.getMaxAge() >= 0
                            ? Instant.now().plusSeconds(cookie.getMaxAge()) : null;
                    return new AuthMaterial(Map.of(), Map.of(wantedName, cookie.getValue()), expiresAt);
                }
            }
        }
        throw new IllegalStateException("Login response did not contain the configured session cookie");
    }

    private JsonNode login() {
        HttpResponse<String> response = sendLogin();
        try {
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Login response was not valid JSON", e);
        }
    }

    private HttpResponse<String> sendLogin() {
        String url = config.require("auth.login-url");
        ServiceConfig.builder("auth", url).build(); // Validate an absolute HTTP(S) URL without embedded credentials.
        ObjectNode payload = JSON.createObjectNode();
        payload.put(config.get("auth.username-field", "username"), config.require("auth.username"));
        payload.put(config.get("auth.password-field", "password"), config.require("auth.password"));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Authentication endpoint returned HTTP " + response.statusCode());
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Authentication request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication request was interrupted", e);
        }
    }

    private String requiredText(JsonNode value, String description) {
        if (value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("Login response is missing " + description);
        }
        return value.asText();
    }

    /** Builds the provider map used by CredentialManager; mode 'none' requires no provider. */
    public static Map<String, AuthProvider> providers(ConsumerConfig config) {
        String mode = config.get("auth.mode", "none");
        if (mode.equalsIgnoreCase("none")) return Map.of();
        return Map.of(config.get("auth.profile", "api"), new ConfiguredAuthProvider(config));
    }
}
