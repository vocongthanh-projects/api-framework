package com.example.consumer.tests;

import com.example.consumer.database.JdbcQueryClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Optional database connection example; run only with -Pdatabase-tests and DB credentials configured. */
public final class DatabaseSmokeTest {
    @Test(groups = "database")
    public void executesReadOnlySmokeQuery() throws IOException, java.sql.SQLException {
        String sql;
        try (var input = getClass().getResourceAsStream("/sql/database-smoke.sql")) {
            if (input == null) throw new IllegalStateException("Missing /sql/database-smoke.sql");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        Assert.assertEquals(JdbcQueryClient.fromEnvironment().queryForLong(sql), 1L);
    }
}
