# API Framework Execution Flows

This document describes the framework's three main flows in chronological order: **test initialization**, **request execution**, and **post-test cleanup**.

## 1. Test initialization

Initialization is split into two layers:

- The runtime layer runs once per test class.
- The context layer runs once per test method.

```mermaid
sequenceDiagram
    autonumber
    participant TestNG
    participant Consumer as Project Base Test
    participant Core as BaseApiTest
    participant Runtime as ApiTestRuntime
    participant Holder as TestContextHolder
    participant Client as ApiClient

    Note over TestNG,Client: @BeforeClass — once per test class
    TestNG->>Core: initializeApiRuntime()
    Core->>Consumer: beforeApiClass()
    Core->>Consumer: createRuntime()
    Consumer->>Consumer: Build FrameworkConfig
    Consumer->>Consumer: Build CredentialManager
    Consumer->>Client: new RestAssuredApiClient(config, credentials, traceSink)
    Consumer->>Runtime: new ApiTestRuntime(config, credentials, client)
    Runtime-->>Core: runtime

    Note over TestNG,Client: @BeforeMethod — once per test method
    TestNG->>Core: initializeApiTestContext(method)
    Core->>Core: new TestContext(testId)
    Core->>Core: new ResourceLedger()
    Core->>Holder: set(context)
    Note right of Holder: ThreadLocal belongs only to the TestNG module
    Core->>Consumer: beforeApiTest()
    Core-->>TestNG: Test is ready
```

### Rationale

1. `ApiTestRuntime` owns configuration, the credential cache, and the HTTP client, so it is created once per test class.
2. A fresh `TestContext` and `ResourceLedger` are created for every method to prevent trace and resource sharing between tests.
3. Consumers wire the runtime once in their project base test; business tests reuse `api()`, `context()`, and `resources()`.

## 2. Request execution and authentication

This flow starts when a domain client calls `api.execute(ApiRequest)`.

```mermaid
sequenceDiagram
    autonumber
    participant Test as Test Method
    participant Domain as Domain Client
    participant Client as RestAssuredApiClient
    participant Cred as CredentialManager
    participant Provider as AuthProvider
    participant API as Backend API
    participant Redactor
    participant Sink as TraceSink
    participant Context as TestContext

    Test->>Domain: createEmployee(request)
    Domain->>Client: execute(ApiRequest)
    Client->>Client: Resolve ServiceConfig
    Client->>Cred: get(authProfile, actor)

    alt Token is missing or near expiry
        Cred->>Provider: authenticate(actor)
        Provider-->>Cred: AuthMaterial
        Cred->>Cred: Cache by profile and actor alias
    else Cached token is still valid
        Cred->>Cred: Reuse current AuthMaterial
    end

    Cred-->>Client: Valid AuthMaterial
    Client->>Client: Merge service, auth, and request headers
    Client->>API: Send HTTP request through RestAssured
    API-->>Client: HTTP response
    Client->>Redactor: Mask token, password, cookie, and OTP
    Redactor-->>Client: Redacted request and response
    Client->>Sink: record(ExchangeTrace)
    Sink->>Context: Store the current test trace

    alt First 401 response and service uses authentication
        Client->>Cred: invalidate(authProfile, actor)
        Note right of Cred: Remove only this actor's cache entry
        Client->>Cred: get(authProfile, actor)
        Cred->>Provider: authenticate(actor)
        Provider-->>Cred: New token
        Cred-->>Client: New AuthMaterial
        Client->>API: Retry the request once
        API-->>Client: New HTTP response
        Client->>Sink: record(redacted trace)
        Note over Client,Context: Return to response evaluation
    else Retryable status or exception and RetryPolicy permits retry
        Client->>Client: Exponential backoff with jitter
        Client->>API: Send the next request attempt
        API-->>Client: HTTP response
        Client->>Sink: record(redacted trace)
        Note over Client,Context: Continue until the policy limit
    else Retry is not required
        Client->>Client: End the loop
    end

    alt Non-retryable transport exception
        Client-->>Domain: Throw ApiTransportException
        Domain-->>Test: Propagate error
    else Terminal HTTP response exists
        Client-->>Domain: ApiResponse
        Domain-->>Test: Domain result
    end
```

### Retry rules

| Mode | Behavior |
|---|---|
| `DEFAULT` | Automatically retries only safe methods such as GET, HEAD, and OPTIONS |
| `NEVER` | Never retries |
| `IDEMPOTENT` | Allows mutation retries but requires an `Idempotency-Key` |

A `401` refresh occurs at most once per request. If the response after refresh is still `401`, the framework returns it to the test instead of creating an infinite authentication loop.

## 3. Cleanup and report generation

`@AfterMethod(alwaysRun = true)` runs whether the test passes, fails, or is skipped.

```mermaid
sequenceDiagram
    autonumber
    participant TestNG
    participant Core as BaseApiTest
    participant Ledger as ResourceLedger
    participant API as Backend API
    participant Report as TraceReportWriter
    participant Holder as TestContextHolder

    TestNG->>Core: finalizeApiTestContext(result)
    Core->>Ledger: close()
    Note over Ledger: LIFO — resources created last are removed first

    loop Each cleanup action in reverse order
        Ledger->>API: DELETE child resource
        API-->>Ledger: Cleanup response
    end

    alt Test failed and cleanup also failed
        Ledger-->>Core: CleanupException
        Core->>TestNG: Add cleanup error to suppressed errors
    else Test passed but cleanup failed
        Ledger-->>Core: CleanupException
        Core->>TestNG: Mark the test result as FAILED
    else Cleanup succeeded
        Ledger-->>Core: Done
    end

    Core->>Report: write(context, status, error)
    Report->>Report: Write redacted JSON trace
    Core->>Holder: clear()
    Note right of Holder: Always clear ThreadLocal in finally
```

Example resource order:

```text
Create: Organization A -> Employee B -> Contract C
Delete: Contract C     -> Employee B -> Organization A
```

LIFO ensures that child resources are always removed before their parents.

## 4. Test class shutdown

```mermaid
sequenceDiagram
    autonumber
    participant TestNG
    participant Core as BaseApiTest
    participant Runtime as ApiTestRuntime
    participant Cred as CredentialManager
    participant Consumer as Project Base Test

    Note over TestNG,Consumer: @AfterClass — once per test class
    TestNG->>Core: finalizeApiRuntime()
    Core->>Runtime: close()
    Runtime->>Cred: clear()
    Note right of Cred: Clear the test class credential cache
    Core->>Consumer: afterApiClass()
    Consumer-->>TestNG: Complete
```

## 5. Minimal consumer wiring

```java
public abstract class HrmApiTest extends BaseApiTest {
    @Override
    protected ApiTestRuntime createRuntime() {
        FrameworkConfig config = HrmTestConfig.fromEnvironment();
        CredentialManager credentials = HrmAuth.createCredentialManager(config);

        ApiClient api = new RestAssuredApiClient(
                config,
                credentials,
                new CurrentTestTraceSink());

        return new ApiTestRuntime(config, credentials, api);
    }
}
```

```text
your-consumer-project
├── config       FrameworkConfig from the environment and secret provider
├── auth         AuthProvider for each authentication profile
├── clients      Domain clients; never call RestAssured directly
├── fixtures     Create data and register ResourceLedger cleanup
├── scenarios    Reusable business flows
└── tests        Test matrices and assertions
```
