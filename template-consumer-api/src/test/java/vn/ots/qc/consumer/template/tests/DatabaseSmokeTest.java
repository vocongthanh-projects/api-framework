package vn.ots.qc.consumer.template.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.sql.SqlClient;

/**
 * Optional database connection example; run only with -Pdatabase-tests and DB credentials
 * configured.
 */
public final class DatabaseSmokeTest {
  @Test(groups = "database")
  public void executesReadOnlySmokeQuery() throws IOException, java.sql.SQLException {
    String sql;
    try (var input = getClass().getResourceAsStream("/sql/database-smoke.sql")) {
      if (input == null) throw new IllegalStateException("Missing /sql/database-smoke.sql");
      sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    try (SqlClient client = SqlClient.fromEnvironment()) {
      Assert.assertEquals(client.queryForLong(sql), 1L);
    }
  }
}
