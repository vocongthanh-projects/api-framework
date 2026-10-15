package vn.ots.qc.api.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JDBC implementation with one short-lived connection per operation. */
public final class JdbcSqlClient implements SqlClient {
  private static final Logger log = LoggerFactory.getLogger(JdbcSqlClient.class);
  private final SqlConfig config;

  private JdbcSqlClient(SqlConfig config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  public static JdbcSqlClient create(SqlConfig config) {
    return new JdbcSqlClient(config);
  }

  @Override
  public List<Map<String, Object>> query(String sql, Object... parameters) throws SQLException {
    try (Connection connection = connect();
        PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet resultSet = statement.executeQuery()) {
      ResultSetMetaData metadata = resultSet.getMetaData();
      int columns = metadata.getColumnCount();
      List<Map<String, Object>> rows = new ArrayList<>();
      while (resultSet.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns; i++)
          row.put(metadata.getColumnLabel(i), resultSet.getObject(i));
        rows.add(row);
      }
      return List.copyOf(rows);
    }
  }

  @Override
  public long queryForLong(String sql, Object... parameters) throws SQLException {
    try (Connection connection = connect();
        PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next()) throw new SQLException("Query returned no rows");
      return resultSet.getLong(1);
    }
  }

  @Override
  public int execute(String sql, Object... parameters) throws SQLException {
    try (Connection connection = connect();
        PreparedStatement statement = prepare(connection, sql, parameters)) {
      return statement.executeUpdate();
    }
  }

  @Override
  public boolean ping() {
    try (Connection connection = connect()) {
      return connection.isValid(5);
    } catch (SQLException e) {
      log.warn("SQL ping failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public void close() {
    /* no shared connection or pool to close */
  }

  private Connection connect() throws SQLException {
    Properties properties = new Properties();
    if (config.username() != null) properties.setProperty("user", config.username());
    if (config.password() != null) properties.setProperty("password", config.password());
    return DriverManager.getConnection(config.jdbcUrl(), properties);
  }

  private PreparedStatement prepare(Connection connection, String sql, Object... parameters)
      throws SQLException {
    Objects.requireNonNull(sql, "sql must not be null");
    PreparedStatement statement = connection.prepareStatement(sql);
    if (config.queryTimeoutSeconds() > 0) statement.setQueryTimeout(config.queryTimeoutSeconds());
    if (parameters != null)
      for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
    return statement;
  }
}
