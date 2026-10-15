package vn.ots.qc.api.nosql;

import java.util.Map;
import org.bson.Document;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NoSqlQueryLoaderTest {

  @Test
  public void loadsAndInterpolatesVariables() {
    Map<String, Object> params = Map.of("status", "ACTIVE", "limit", 10);
    String query = NoSqlQueryLoader.loadQuery("/nosql/sample-query.json", params);
    Assert.assertTrue(query.contains("\"ACTIVE\""));
    Assert.assertTrue(query.contains("10"));

    Document doc = NoSqlQueryLoader.toDocument(query);
    Assert.assertEquals(doc.getString("status"), "ACTIVE");
    Assert.assertEquals(doc.getInteger("limit"), Integer.valueOf(10));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void throwsWhenFileNotFound() {
    NoSqlQueryLoader.loadRaw("/nosql/non-existent.json");
  }
}
