# CI/CD

Recommended stages:

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
