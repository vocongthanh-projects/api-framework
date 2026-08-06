# Migration from api-map and api_hrm

## Phase 0: secure and freeze

1. Rotate the exposed GitLab token, Cloudflare cookie and XSRF token.
2. Run secret scanning over Git history.
3. Create a reproducible baseline commit for each consumer project.
4. Pin JDK 17 for Maven and CI.
5. Record baseline pass/fail/skip counts and runtime.

## Phase 1: api-map pilot

1. Implement its encrypted login as an `AuthProvider` in `api-map`, not in framework core.
2. Create Maps domain clients around `ApiClient`.
3. Migrate login, register, forgot-password and logout tests.
4. Compare old and new results for several CI runs.
5. Remove copied `RequestHelper`, `RequestExecutor` and report listener.

## Phase 2: HRM vertical slices

Migrate one complete domain at a time:

1. Organization and employee CRUD.
2. Basic attendance.
3. Multi-actor approval.
4. Leave, overtime and trip.
5. KPI, training and complex cross-service workflows.

For each slice create domain clients, fixtures, scenarios and assertions; then remove the corresponding direct
RestAssured calls. Do not perform a mechanical package-wide rewrite.

## Exit checks

- No `RestAssured.given()` outside the transport module.
- No fixed sleeps in consumer tests.
- No global mutable token or actor state.
- No domain name in framework modules.
- No secrets in source, logs, reports or artifacts.
- CI fails on test failure and on zero selected tests.
- Every API attempt is present in the redacted trace.
- Cleanup executes after both pass and failure.

