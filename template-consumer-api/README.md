# API Consumer Test Template

Starter project for a new API test repository using `api-framework`. This directory is independent of the framework reactor: its POM has no framework `<parent>` and depends only on published artifacts (or artifacts installed locally for development).

## What belongs here

- The template provides Maven/TestNG wiring, a project base test, a domain-client example, configuration placeholders, and an offline smoke contract.
- The API framework provides HTTP execution, redaction, retries, test lifecycle, traces, and resource cleanup.
- A product repository owns real endpoints, DTOs, assertions, credentials, and business test data.

The smoke test uses an in-process JDK HTTP server. It does not contact a real service and contains no product URL or credentials.

## Start from the template

Copy this directory into a new repository, rename the Maven coordinates in `pom.xml`, then choose a released framework version for `api.framework.version`. Use a released version for shared/production CI; `1.0.1-SNAPSHOT` is intended for framework development.

### Local framework development

Install the framework artifacts into the local Maven repository first:

```bash
cd ../api-framework
./mvnw clean install
cd template-consumer-api
./mvnw clean verify
```

The template contains its own Maven Wrapper, so the copied project does not depend on a sibling framework checkout after the artifacts are installed.

### GitLab Maven Package Registry

The API framework publishes its Maven artifacts to the GitLab Package Registry. Configure the framework project ID and credentials in the consumer CI variables. The sample CI job uses `CI_API_V4_URL`, `API_FRAMEWORK_PROJECT_ID`, and GitLab's `CI_JOB_TOKEN`; the framework project must allow the consumer project to access its job token.

For local registry access, add a Maven server with ID `api-framework-registry` to `~/.m2/settings.xml`, and activate the URL property when building. Configure a read-package token using the authentication method supported by your GitLab instance:

```xml
<settings>
  <servers>
    <server>
      <id>api-framework-registry</id>
      <username>YOUR_GITLAB_USERNAME_OR_TOKEN_USER</username>
      <password>USE_A_LOCAL_SECRET_OR_ENVIRONMENT_VARIABLE</password>
    </server>
  </servers>
</settings>
```

```bash
./mvnw -Dapi.framework.version=1.0.0 \
  -Dapi.framework.repository.url=https://<GITLAB_DOMAIN>/api/v4/projects/<PROJECT_ID>/packages/maven \
  clean verify
```

Never commit personal tokens or real credentials. Keep secrets in Maven user settings, a secret manager, or masked/protected CI variables.

## Configuration and implementation

`src/test/resources/config.properties` and `config-local.properties.example` are placeholders for the consumer's own configuration loader. The sample smoke test intentionally uses a local in-process endpoint instead. The framework itself accepts typed configuration through `FrameworkConfig`; it does not automatically interpret arbitrary application property keys.

Copy `config-local.properties.example` to `config-local.properties` for local-only values. That file is gitignored. Do not put passwords or tokens in committed configuration. In CI, use protected and masked variables and construct `CredentialManager` from those values.

Adapt `BaseProjectApiTest.createRuntime()` to build `FrameworkConfig`, a `CredentialManager`, and `RestAssuredApiClient`. Keep each product's endpoints in consumer-owned domain clients like `SampleApiClient`; do not call RestAssured directly. The sample registers its created item with `resources().register(...)`; `BaseApiTest` runs that cleanup after the test method even when the assertion fails.

## Commands

```bash
./mvnw clean verify
./mvnw -Dtest=SmokeApiTest test
```

Surefire XML is written to `target/surefire-reports/`; redacted exchange traces are written to `target/api-traces/`.
