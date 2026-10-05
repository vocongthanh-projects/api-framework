package vn.ots.qc.consumer.template.database;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Minimal consumer-side JDBC example for occasional checks; it opens and closes one connection per query. */
public final class JdbcQueryClient {
    private final String url;
    private final Properties credentials;

    private JdbcQueryClient(String url, Properties credentials) {
        this.url = url;
        this.credentials = credentials;
    }

    public static JdbcQueryClient fromEnvironment() {
        String url = System.getenv("DB_JDBC_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Set DB_JDBC_URL to enable database tests");
        }
        Properties credentials = new Properties();
        putIfPresent(credentials, "user", System.getenv("DB_USERNAME"));
        putIfPresent(credentials, "password", System.getenv("DB_PASSWORD"));
        return new JdbcQueryClient(url, credentials);
    }

    /** Executes a parameterized read query and returns the first column of the first row as a long. */
    public long queryForLong(String sql, Object... parameters) throws SQLException {
        try (var connection = DriverManager.getConnection(url, credentials);
             var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Query returned no rows");
                }
                return result.getLong(1);
            }
        }
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        if (value != null) properties.setProperty(key, value);
    }
}
