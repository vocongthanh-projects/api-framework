package vn.ots.qc.api.contract;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.ots.qc.api.core.http.ApiResponse;

/** Validates JSON API responses against named JSON Schema documents. */
public final class JsonSchemaAssertions {
  private final SchemaRegistry registry;

  /**
   * Creates a validator with schemas keyed by their retrieval URI. Referenced schemas must be
   * included in the same map and referenced by their URI from {@code $ref}; remote schema fetches
   * are disabled.
   */
  public JsonSchemaAssertions(Map<String, String> schemas) {
    if (schemas == null || schemas.isEmpty()) {
      throw new IllegalArgumentException("At least one JSON schema is required");
    }
    Map<String, String> schemaDocuments = Map.copyOf(schemas);
    this.registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder ->
                builder.schemaLoader(
                    loader ->
                        loader
                            .fetchRemoteResources(false)
                            .resourceLoaders(resources -> resources.resources(schemaDocuments))));
  }

  /** Fails with a concise list of schema paths and rules that the response violates. */
  public void requireValid(ApiResponse response, String schemaUri) {
    Objects.requireNonNull(response, "response");
    if (schemaUri == null || schemaUri.isBlank()) {
      throw new IllegalArgumentException("schemaUri is required");
    }
    if (response.body().isBlank()) {
      throw new AssertionError("Expected a JSON response body for schema " + schemaUri);
    }

    Schema schema = registry.getSchema(SchemaLocation.of(schemaUri));
    List<Error> errors = schema.validate(response.body(), InputFormat.JSON);
    if (errors.isEmpty()) return;

    String details =
        errors.stream()
            .map(error -> "- " + error.getInstanceLocation() + ": " + error.getMessage())
            .collect(java.util.stream.Collectors.joining("\n"));
    throw new AssertionError("Response does not match JSON schema " + schemaUri + ":\n" + details);
  }
}
