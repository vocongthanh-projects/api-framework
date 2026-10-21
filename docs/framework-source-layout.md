# Framework Source Layout

This document defines the source-organization rules shared by the API, Appium, and Playwright framework repositories. The rules align ownership and navigation without forcing different languages and runners into an identical directory tree.

## Shared rules

- Keep reusable, product-neutral framework capabilities in framework modules/packages. Product endpoints, DTOs, screens, pages, selectors, workflows, credentials, and business scenarios belong to consumer repositories.
- Keep artifact/module boundaries based on independently reusable capabilities. Do not create one module per class or merge distinct published capabilities into a generic `common`/`utils` module.
- Group implementation code by capability or responsibility. Keep related contract tests with the module that owns the code; mirror Java production packages under that module's test source root.
- Keep adapters dependent on lower-level core capabilities. Core code must not depend on optional runners or product-specific behavior.
- Keep build/configuration files, documentation, and maintenance scripts in their established repository-level locations. Do not commit generated output such as `target/`, `dist/`, reports, or test results.
- Preserve existing public package names, artifact coordinates, and build boundaries unless a change is explicitly required and all consumers can migrate together.

## Java and Maven

Each existing Maven module uses Maven's standard roots:

```text
<module>/
├── src/main/java/<package-root>/<capability>/...   # reusable implementation
├── src/main/resources/                             # framework-owned resources
├── src/test/java/<package-root>/<capability>/...   # module contract/unit tests
└── src/test/resources/                            # test-only resources
```

Use the repository's existing group/package namespace (`api`, `mobile`, or `web`) and the capability's responsibility below it. Keep integration adapters such as TestNG and Cucumber in their existing Maven modules. Consumer product code is not placed in framework production packages.

## Playwright TypeScript

The TypeScript framework remains an independent npm package:

```text
playwright-framework-typescript/
├── src/index.ts                 # supported package entry point
├── src/                         # implementation, grouped by capability when useful
├── test/                        # framework contract tests at configured testDir
├── package.json
└── playwright.config.ts
```

Keep the public package surface explicit through `src/index.ts`. A flat `src/` is appropriate while the small set of files remains easy to navigate; introduce capability subfolders when related implementation grows enough to benefit. Keep Playwright configuration and its configured test directory in the locations expected by the package scripts. Do not mirror Maven-specific `src/main` or `src/test` paths in TypeScript.

## Stack vocabulary

The frameworks share ownership and dependency rules, not identical capability names. API transport, mobile driver/screen primitives, and browser/page primitives remain in their respective frameworks. Consumer source organization is defined by the consumer source layout document in each repository.
