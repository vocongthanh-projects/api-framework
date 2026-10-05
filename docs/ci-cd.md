# CI/CD

## 1. Engine Pipeline (Current Framework Repository)

The `api-framework` repository runs a fast, self-contained pipeline across 3 stages:

```text
validate -> test -> deploy
```

- **validate**: Compiles and packages all reactor modules with sources and javadocs attached (`-DskipTests package`).
- **test**: Executes the framework test suite and installs the reactor artifacts locally, then runs `template-consumer-api/` against those artifacts with `clean verify`. This checks the consumer starter before publication without requiring a GitLab package round trip. Publishes Surefire JUnit XML reports + redacted API traces.
- **deploy**: Deploys `api-framework-parent` and the 4 core libraries (`framework-core`, `framework-http-restassured`, `framework-fixtures`, `framework-testng`) along with their `-sources.jar` and `-javadoc.jar` to the GitLab Package Registry. `starter-example` is explicitly skipped.

## 2. Consumer Template Pipeline

The independent starter project in `template-consumer-api/` has its own `.gitlab-ci.yml`; it is not part of the framework reactor pipeline. The job:

1. Creates a temporary Maven `settings.xml` using GitLab's built-in `CI_JOB_TOKEN`.
2. Reads `API_FRAMEWORK_PROJECT_ID` from the consumer project's CI/CD variables.
3. Resolves the framework from the project Maven endpoint and runs `clean verify`.
4. Publishes Surefire XML and redacted API traces as job artifacts.

For the current Framework API project, set `API_FRAMEWORK_PROJECT_ID=1263`. Add the consumer project to the framework project's CI/CD job-token allowlist so its token can read the private package. `API_FRAMEWORK_VERSION` is optional and defaults to `1.0.1-SNAPSHOT`; select a published release version for a stable consumer pipeline.

The template smoke test uses an in-process JDK HTTP server. It checks Maven dependency resolution and the framework wiring without contacting a real service. Consumer API tests that need a product environment must receive their URL and credentials from protected/masked CI variables or a secret manager.

## 3. Consumer Test Suite Blueprint (Target Architecture for `api_hrm`, `api-map`)

For consumer repositories containing large enterprise regression suites (hundreds or thousands of API test scenarios), the recommended target pipeline is:

```text
validate -> unit -> smoke -> regression shards -> aggregate -> publish -> notify
```

Validation should include compilation, formatting, static analysis, dependency checks, secret scanning and an
architecture rule that rejects direct RestAssured use in consumer code.

The pipeline must use the repository already checked out by the CI runner. Never download source using a token
embedded in YAML. Test failures must produce a non-zero build result; reporting and notification jobs may use
`when: always` but must not overwrite that result.

Start parallel execution at class level after migration. Keep shared-tenant and destructive suites serial.
Shard regression tests by historical duration rather than class count, and distinguish runner retry from test retry.

Publish these artifacts:

- Surefire XML as the CI source of truth.
- `target/api-traces` from every shard.
- Optional Allure output generated from traces.
- Optional Excel generated after execution, never inside the test listener.
