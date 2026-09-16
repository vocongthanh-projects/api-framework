# CI/CD

## 1. Engine Pipeline (Current Framework Repository)

The `api-framework` repository runs a fast, self-contained pipeline across 3 stages:

```text
validate -> test -> deploy
```

- **validate**: Compiles and packages all reactor modules with sources and javadocs attached (`-DskipTests package`).
- **test**: Executes full test suite (`./mvnw verify`), ensuring 100% pass rate and publishing surefire JUnit XML reports + redacted API traces.
- **deploy**: Deploys `api-framework-parent` and the 4 core libraries (`framework-core`, `framework-http-restassured`, `framework-fixtures`, `framework-testng`) along with their `-sources.jar` and `-javadoc.jar` to a package registry. `starter-example` is explicitly skipped.

## 2. Consumer Test Suite Blueprint (Target Architecture for Large Consumer Projects)

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
