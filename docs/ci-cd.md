# CI/CD

## OTS/FLI source pipeline rule

Every source push/MR pipeline is lightweight. It may validate configuration and versions, resolve dependencies, compile production and test sources, run static checks, and package artifacts. It must not execute API, browser/UI, Appium/mobile, database, integration, or product tests.

Full test suites run through a separately configured test infrastructure project/runner, using its own schedule or explicit trigger. Do not add product URLs, credentials, or local config files to a source compile pipeline.

## Framework API source pipeline

The `framework-api` GitLab project runs:

```text
validate/compile -> package deploy -> docs -> consumer compile compatibility -> Telegram notification
```

- `validate` builds and installs framework modules with `-DskipTests`, then runs the consumer template's `clean verify -DskipTests`. Maven compiles test sources but does not execute them.
- `deploy` publishes Maven artifacts on configured branch/tag rules, using the project registry and `CI_JOB_TOKEN`.
- `consumer-compatibility` triggers `api-ag270` after the framework package is published. The consumer pipeline resolves the exact framework version and compiles every module with `-DskipTests`; it does not run TestNG or contact product APIs.
- `notify` reports pipeline status to Telegram. Notification failure does not change the compile result.

The current project path is `qc-quality-control/framework-api` (GitLab project ID `1263`), with `develop` as the integration branch. The GitHub mirror uses `main`; verify both trees match after source changes.

## API consumer source pipeline

The `api-ag270` compile job requires `API_FRAMEWORK_PROJECT_ID` so Maven can resolve the framework through the project package registry. Cross-project package access uses GitLab job-token permissions/allowlist; do not add a broad PAT just for dependency resolution.

The root CI runs `clean verify -DskipTests`. This compiles consumer test sources and resolves dependencies without executing TestNG. Product service URLs and test credentials are not required by this pipeline.

## Consumer test suites

The dedicated test infrastructure owns schedules/triggers, runner tags, product environment settings, test identities, device/browser availability, and test-result reporting. Keep its configuration independent from source push pipelines. It may include test-only templates, but source `.gitlab-ci.yml` files must not trigger those templates on push/MR.

For API suites that need database access, protect the connection variables and use a runner with explicit test database access. Browser/Appium suites likewise require dedicated browsers/devices and must run on their designated infrastructure. Never assume the GitLab host or an untagged shared runner has product connectivity or test devices.

## Telegram result notifications

Configure masked `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_GITLAB_OTS_FLI`, and `TELEGRAM_THREAD_GITLAB_OTS_FLI` variables in GitLab. Success/failure jobs report source pipeline outcomes to the company topic; notification failures are allowed to fail without changing the pipeline result.
