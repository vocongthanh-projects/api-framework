package vn.ots.qc.api.sql;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Small JDBC client intended for database assertions in API tests. */
public interface SqlClient extends AutoCloseable {
  /** Executes a parameterized query and returns rows keyed by column label. */
  List<Map<String, Object>> query(String sql, Object... parameters) throws SQLException;

  /** Returns the first column of the first row as a long. */
  long queryForLong(String sql, Object... parameters) throws SQLException;

  /** Executes a parameterized update/statement and returns JDBC's affected-row count. */
  int execute(String sql, Object... parameters) throws SQLException;

  /** Opens a connection and checks connectivity. */
  boolean ping();

  /** DriverManager connections are scoped to each operation; close is a no-op. */
  @Override
  void close();

  static SqlClient create(SqlConfig config) {
    return JdbcSqlClient.create(config);
  }

  static SqlClient fromEnvironment() {
    return create(SqlConfig.fromEnvironment());
  }
}
