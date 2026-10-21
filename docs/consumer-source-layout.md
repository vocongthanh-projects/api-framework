# Consumer Source Layout Standard

This shared convention describes product test code. It does not prescribe the internal package layout of the reusable framework libraries, which remain grouped by reusable capability.

## Shared terms

- **Portal**: an independently addressed product domain or application. A project that covers one application still uses one portal root so later growth has a clear place.
- **Module**: a feature area or tab inside a portal.
- **Service**: an API contract boundary inside a module. HTTP verbs are operations of a service client, not package names. UI automation does not invent service packages when it has no API clients.
- **Workflow**: a scenario that intentionally crosses modules or portals; place its tests under `workflows/`.
- **Ownership**: request/response models, pages/screens, and feature tests live with the portal/module that owns their behavior. Shared code is promoted only when ownership and semantics are truly shared.

## Stack-specific layout

Use the same portal/module ownership, with the names natural to each test stack:

```text
API consumer (Java/Maven)
src/test/java/<package_root>/
├── auth/ base/ config/ data/ runtime/           # consumer infrastructure
├── portals/<portal>/
│   ├── shared/model/                            # stable contracts shared inside this portal
│   └── modules/<module>/
│       ├── client/<service>/                    # API clients by service boundary
│       ├── model/<service>/                     # request/response contracts by service
│       └── tests/                               # feature suites
├── shared/<service>/{client,model}/             # true cross-portal API contracts/adapters
└── workflows/<workflow>/tests/                  # cross-module/portal scenarios

Appium consumer (Java/Maven)
src/test/java/<package_root>/
├── base/ config/ data/                           # consumer infrastructure
├── portals/<app>/modules/<module>/
│   ├── screens/                                 # screen objects and logical locators
│   ├── components/                              # reusable screen regions, when needed
│   └── tests/                                   # feature scenarios
└── workflows/<workflow>/tests/                  # journeys across modules

Playwright consumer (Java/Maven)
src/test/java/<package_root>/
├── base/ config/ fixtures/                       # consumer infrastructure
├── portals/<portal>/modules/<module>/
│   ├── pages/                                   # page objects
│   ├── components/                              # reusable UI components, when needed
│   └── tests/                                   # feature scenarios
└── workflows/<workflow>/tests/                  # journeys across modules

Playwright consumer (TypeScript)
fixtures.ts / playwright.config.ts                # native runner setup stays at project root
pages/<portal>/<module>/                          # page objects
components/<portal>/<module>/                    # reusable components, when needed
tests/<portal>/<module>/*.spec.ts                # feature specs
workflows/<workflow>/*.spec.ts                   # cross-module journeys
```

`src/test/java` and `src/test/resources` are Maven's standard source/resource roots. The product-oriented subdirectories above are this organization's convention; Maven, Appium, and Playwright do not mandate this product taxonomy. Keep Cucumber feature files under `src/test/resources/features/<portal>/<module>/` and glue under the matching Java package when using Cucumber.

## Why the trees differ

The ownership model is shared; the implementation vocabulary is not. API clients own endpoint contracts, Appium screens own native UI interactions, and Playwright pages own web UI interactions. Playwright TypeScript keeps its root fixtures and test discovery idiomatic to Playwright Test; it does not add Java-style base classes or Maven packages.

## Reference basis

- Maven Standard Directory Layout defines the standard roots such as `src/test/java` and `src/test/resources`; it does not define product package taxonomies.
- Playwright documents fixtures and Page Object Model as native test organization mechanisms.
- Appium's Java quickstart shows how to connect its client to a test framework but does not mandate a consumer folder hierarchy. The Appium consumer tree here is the local convention.

See [Maven Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html), [Playwright fixtures](https://playwright.dev/docs/test-fixtures), [Playwright Page Object Models](https://playwright.dev/docs/pom), and [Appium Write a Test (Java)](https://appium.io/docs/en/latest/quickstart/test-java/).
