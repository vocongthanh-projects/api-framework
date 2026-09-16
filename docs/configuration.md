# Configuration

Version 1 deliberately has no classpath configuration DSL. Consumers build immutable typed configuration from
environment variables or their existing configuration provider. This keeps the framework aligned with common
consumer-project deployment models.

```java
FrameworkConfig config = FrameworkConfig.builder()
    .environment(System.getenv().getOrDefault("TEST_ENV", "local"))
    .service(ServiceConfig.builder("hrm", requiredEnv("HRM_BASE_URL"))
        .authProfile("admin")
        .header("X-Tenant-Code", requiredEnv("TENANT_CODE"))
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(30))
        .build())
    .service(ServiceConfig.builder("iam", requiredEnv("IAM_BASE_URL"))
        .authProfile("admin")
        .build())
    .retry(RetryConfig.defaults())
    .build();
```

Create one `ApiTestRuntime` in the consumer project's base test class:

```java
public abstract class HrmApiTest extends BaseApiTest {
    @Override
    protected ApiTestRuntime createRuntime() {
        FrameworkConfig config = HrmTestConfig.fromEnvironment();
        CredentialManager credentials = HrmAuth.createCredentialManager(config);
        ApiClient client = new RestAssuredApiClient(config, credentials, new CurrentTestTraceSink());
        return new ApiTestRuntime(config, credentials, client);
    }
}
```

Do not store passwords, access tokens, refresh tokens, OTPs, private keys, Cloudflare cookies or CI tokens in
properties files. Put them in masked/protected CI variables or a secret manager.

TLS verification is enabled by default. `relaxedTls(true)` is permitted only for an explicitly selected local
environment; CI and shared environments must use valid certificates.
