package vn.ots.qc.api.sql;

import java.util.Objects;
import java.util.Properties;

/** Connection settings for a vendor-provided JDBC driver. */
public final class SqlConfig {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int queryTimeoutSeconds;

    private SqlConfig(Builder builder) {
        jdbcUrl = Objects.requireNonNull(builder.jdbcUrl, "JDBC URL must not be null").trim();
        if (jdbcUrl.isEmpty()) throw new IllegalArgumentException("JDBC URL must not be blank");
        username = builder.username;
        password = builder.password;
        if (builder.queryTimeoutSeconds < 0) throw new IllegalArgumentException("queryTimeoutSeconds must be >= 0");
        queryTimeoutSeconds = builder.queryTimeoutSeconds;
    }

    public String jdbcUrl() { return jdbcUrl; }
    public String username() { return username; }
    public String password() { return password; }
    /** Zero means the JDBC driver default. */
    public int queryTimeoutSeconds() { return queryTimeoutSeconds; }

    public static Builder builder() { return new Builder(); }

    public static SqlConfig fromEnvironment() {
        String url = System.getenv("SQL_JDBC_URL");
        if (url == null || url.isBlank()) url = System.getenv("DB_JDBC_URL");
        if (url == null || url.isBlank()) throw new IllegalStateException("Set SQL_JDBC_URL or DB_JDBC_URL to configure SQL client");
        return builder().jdbcUrl(url).username(firstPresent(System.getenv("SQL_USERNAME"), System.getenv("DB_USERNAME")))
                .password(firstPresent(System.getenv("SQL_PASSWORD"), System.getenv("DB_PASSWORD"))).build();
    }

    public static SqlConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String url = properties.getProperty("sql.jdbcUrl", properties.getProperty("db.jdbcUrl"));
        if (url == null || url.isBlank()) throw new IllegalStateException("Missing 'sql.jdbcUrl' property");
        return builder().jdbcUrl(url).username(properties.getProperty("sql.username", properties.getProperty("db.username")))
                .password(properties.getProperty("sql.password", properties.getProperty("db.password"))).build();
    }

    private static String firstPresent(String first, String second) { return first == null || first.isBlank() ? second : first; }

    public static final class Builder {
        private String jdbcUrl;
        private String username;
        private String password;
        private int queryTimeoutSeconds;
        public Builder jdbcUrl(String value) { jdbcUrl = value; return this; }
        public Builder username(String value) { username = value; return this; }
        public Builder password(String value) { password = value; return this; }
        public Builder queryTimeoutSeconds(int value) { queryTimeoutSeconds = value; return this; }
        public SqlConfig build() { return new SqlConfig(this); }
    }
}
