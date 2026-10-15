package vn.ots.qc.api.nosql;

import java.util.Properties;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NoSqlConfigTest {

  @Test
  public void buildsConfigCorrectly() {
    NoSqlConfig config =
        NoSqlConfig.builder()
            .uri("mongodb://localhost:27017")
            .defaultDatabase("test_db")
            .connectTimeoutMs(3000)
            .build();

    Assert.assertEquals(config.uri(), "mongodb://localhost:27017");
    Assert.assertEquals(config.defaultDatabase(), "test_db");
    Assert.assertEquals(config.connectTimeoutMs(), 3000);
  }

  @Test
  public void loadsFromProperties() {
    Properties props = new Properties();
    props.setProperty("nosql.uri", "mongodb://cluster:27017");
    props.setProperty("nosql.database", "production");

    NoSqlConfig config = NoSqlConfig.fromProperties(props);
    Assert.assertEquals(config.uri(), "mongodb://cluster:27017");
    Assert.assertEquals(config.defaultDatabase(), "production");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void rejectsBlankUri() {
    NoSqlConfig.builder().uri("   ").build();
  }
}
