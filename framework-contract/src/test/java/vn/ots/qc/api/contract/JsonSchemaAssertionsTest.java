package vn.ots.qc.api.contract;

import java.time.Duration;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.http.ApiResponse;

public class JsonSchemaAssertionsTest {
  private static final String USER_SCHEMA = "memory:/schemas/user.json";

  @Test
  public void acceptsValidResponseAndLocalReferences() {
    JsonSchemaAssertions assertions =
        new JsonSchemaAssertions(
            Map.of(
                USER_SCHEMA,
                """
                {"$schema":"https://json-schema.org/draft/2020-12/schema", "$ref":"memory:/schemas/user-fields.json"}
                """,
                "memory:/schemas/user-fields.json",
                """
                {"type":"object", "required":["id"], "properties":{"id":{"type":"integer"}}}
                """));

    assertions.requireValid(response("{\"id\":42}"), USER_SCHEMA);
  }

  @Test
  public void reportsInvalidInstancePathWithoutEchoingTheResponse() {
    JsonSchemaAssertions assertions =
        new JsonSchemaAssertions(
            Map.of(
                USER_SCHEMA,
                """
                {"$schema":"https://json-schema.org/draft/2020-12/schema", "type":"object", "required":["password"], "properties":{"password":{"type":"string"}}}
                """));

    AssertionError error =
        Assert.expectThrows(
            AssertionError.class,
            () -> assertions.requireValid(response("{\"name\":\"private value\"}"), USER_SCHEMA));

    Assert.assertTrue(error.getMessage().contains(USER_SCHEMA));
    Assert.assertFalse(error.getMessage().contains("private value"));
  }

  private ApiResponse response(String body) {
    return new ApiResponse(200, Map.of(), body, Duration.ZERO, 1);
  }
}
