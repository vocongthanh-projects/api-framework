package vn.ots.qc.api.nosql;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Configuration holder for NoSQL database connections.
 */
public final class NoSqlConfig {
    private final String uri;
    private final String defaultDatabase;
    private final int connectTimeoutMs;
    private final int serverSelectionTimeoutMs;

    private NoSqlConfig(Builder builder) {
        this.uri = Objects.requireNonNull(builder.uri, "NoSQL URI must not be null");
        if (this.uri.isBlank()) {
            throw new IllegalArgumentException("NoSQL URI must not be blank");
        }
        this.defaultDatabase = builder.defaultDatabase != null ? builder.defaultDatabase.trim() : "";
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.serverSelectionTimeoutMs = builder.serverSelectionTimeoutMs;
        if (connectTimeoutMs <= 0) throw new IllegalArgumentException("connectTimeoutMs must be > 0");
        if (serverSelectionTimeoutMs <= 0) throw new IllegalArgumentException("serverSelectionTimeoutMs must be > 0");
    }

    public String uri() { return uri; }
    public String defaultDatabase() { return defaultDatabase; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
    public int serverSelectionTimeoutMs() { return serverSelectionTimeoutMs; }

    public static Builder builder() { return new Builder(); }

    public static NoSqlConfig fromEnvironment() {
        String uri = System.getenv("NOSQL_URI");
        if (uri == null || uri.isBlank()) {
            uri = System.getenv("MONGO_URI");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("Set NOSQL_URI or MONGO_URI to configure NoSQL client");
        }

        String db = System.getenv("NOSQL_DATABASE");
        if (db == null || db.isBlank()) {
            db = System.getenv("MONGO_DATABASE");
        }

        return builder().uri(uri).defaultDatabase(db).build();
    }

    public static NoSqlConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String uri = properties.getProperty("nosql.uri", properties.getProperty("mongo.uri"));
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("Missing 'nosql.uri' or 'mongo.uri' property");
        }
        String db = properties.getProperty("nosql.database", properties.getProperty("mongo.database", ""));
        return builder().uri(uri).defaultDatabase(db).build();
    }

    public static final class Builder {
        private String uri;
        private String defaultDatabase = "";
        private int connectTimeoutMs = (int) TimeUnit.SECONDS.toMillis(5);
        private int serverSelectionTimeoutMs = (int) TimeUnit.SECONDS.toMillis(5);

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder defaultDatabase(String defaultDatabase) {
            this.defaultDatabase = defaultDatabase;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder serverSelectionTimeoutMs(int serverSelectionTimeoutMs) {
            this.serverSelectionTimeoutMs = serverSelectionTimeoutMs;
            return this;
        }

        public NoSqlConfig build() {
            return new NoSqlConfig(this);
        }
    }
}
