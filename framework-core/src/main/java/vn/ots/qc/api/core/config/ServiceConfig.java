package vn.ots.qc.api.core.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ServiceConfig(
        String name,
        URI baseUri,
        String authProfile,
        Map<String, String> defaultHeaders,
        Duration connectTimeout,
        Duration readTimeout,
        boolean relaxedTls) {

    public ServiceConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name is required");
        Objects.requireNonNull(baseUri, "Service baseUri is required");
        if (!Set.of("http", "https").contains(baseUri.getScheme()) || baseUri.getHost() == null) {
            throw new IllegalArgumentException("Service baseUri must be an absolute HTTP(S) URI");
        }
        if (baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Service baseUri must not contain credentials");
        }
        authProfile = authProfile == null ? "none" : authProfile;
        defaultHeaders = Map.copyOf(defaultHeaders == null ? Map.of() : defaultHeaders);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("Service timeouts must be greater than zero");
        }
    }

    public static Builder builder(String name, String baseUri) {
        return new Builder(name, URI.create(baseUri));
    }

    public static final class Builder {
        private final String name;
        private final URI baseUri;
        private String authProfile = "none";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
        private boolean relaxedTls;

        private Builder(String name, URI baseUri) { this.name = name; this.baseUri = baseUri; }
        public Builder authProfile(String value) { this.authProfile = value; return this; }
        public Builder header(String name, String value) { this.headers.put(name, value); return this; }
        public Builder connectTimeout(Duration value) { this.connectTimeout = value; return this; }
        public Builder readTimeout(Duration value) { this.readTimeout = value; return this; }
        public Builder relaxedTls(boolean value) { this.relaxedTls = value; return this; }
        public ServiceConfig build() {
            return new ServiceConfig(name, baseUri, authProfile, headers, connectTimeout, readTimeout, relaxedTls);
        }
    }
}
