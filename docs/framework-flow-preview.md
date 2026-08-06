# Luồng hoạt động của API Framework

Tài liệu này mô tả ba luồng chính của framework theo thứ tự thời gian: **khởi tạo test**,
**thực thi request** và **dọn dẹp sau test**.

---

## 1. Luồng khởi tạo test

Framework chia khởi tạo thành hai tầng:

- Tầng runtime chạy một lần cho mỗi test class.
- Tầng context chạy lại cho từng test method.

```mermaid
sequenceDiagram
    autonumber
    participant TestNG
    participant Consumer as Project Base Test
    participant Core as BaseApiTest
    participant Runtime as ApiTestRuntime
    participant Holder as TestContextHolder
    participant Client as ApiClient

    Note over TestNG,Client: @BeforeClass — chạy 1 lần cho mỗi test class
    TestNG->>Core: initializeApiRuntime()
    Core->>Consumer: beforeApiClass()
    Core->>Consumer: createRuntime()
    Consumer->>Consumer: Build FrameworkConfig
    Consumer->>Consumer: Build CredentialManager
    Consumer->>Client: new RestAssuredApiClient(config, credentials, traceSink)
    Consumer->>Runtime: new ApiTestRuntime(config, credentials, client)
    Runtime-->>Core: runtime

    Note over TestNG,Client: @BeforeMethod — chạy cho mỗi test method
    TestNG->>Core: initializeApiTestContext(method)
    Core->>Core: new TestContext(testId)
    Core->>Core: new ResourceLedger()
    Core->>Holder: set(context)
    Note right of Holder: ThreadLocal chỉ thuộc module TestNG
    Core->>Consumer: beforeApiTest()
    Core-->>TestNG: Test sẵn sàng chạy
```

### Ý nghĩa

1. `ApiTestRuntime` giữ config, credential cache và HTTP client nên chỉ được tạo một lần cho test class.
2. `TestContext` và `ResourceLedger` được tạo mới cho từng method, tránh chia sẻ trace và resource giữa các test.
3. Consumer chỉ cần wiring một lần trong project base test; test business dùng lại `api()`, `context()` và `resources()`.

---

## 2. Luồng thực thi request và authentication

Đây là luồng chạy khi domain client gọi `api.execute(ApiRequest)`.

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

    alt Token chưa có hoặc sắp hết hạn
        Cred->>Provider: authenticate(actor)
        Provider-->>Cred: AuthMaterial
        Cred->>Cred: Cache theo profile + actor alias
    else Token cache còn dùng được
        Cred->>Cred: Dùng lại AuthMaterial hiện tại
    end

    Cred-->>Client: AuthMaterial hợp lệ
    Client->>Client: Merge service + auth + request headers
    Client->>API: Gửi HTTP request bằng RestAssured
    API-->>Client: HTTP response
    Client->>Redactor: Che token, password, cookie, OTP
    Redactor-->>Client: Request/response đã redacted
    Client->>Sink: record(ExchangeTrace)
    Sink->>Context: Lưu trace của test hiện tại

    alt Response là 401 lần đầu và service có auth
        Client->>Cred: invalidate(authProfile, actor)
        Note right of Cred: Xóa đúng cache entry của actor
        Client->>Cred: get(authProfile, actor)
        Cred->>Provider: authenticate(actor)
        Provider-->>Cred: Token mới
        Cred-->>Client: AuthMaterial mới
        Client->>API: Gửi lại request một lần
        API-->>Client: HTTP response mới
        Client->>Sink: record(redacted trace)
        Note over Client,Context: Quay lại bước đánh giá response
    else Status/exception retryable và RetryPolicy cho phép
        Client->>Client: Exponential backoff + jitter
        Client->>API: Gửi request attempt tiếp theo
        API-->>Client: HTTP response
        Client->>Sink: record(redacted trace)
        Note over Client,Context: Tiếp tục vòng lặp đến giới hạn policy
    else Không cần retry
        Client->>Client: Kết thúc vòng lặp
    end

    alt Có transport exception không thể retry
        Client-->>Domain: Throw ApiTransportException
        Domain-->>Test: Propagate error
    else Có terminal HTTP response
        Client-->>Domain: ApiResponse
        Domain-->>Test: Domain result
    end
```

### Quy tắc retry

| Chế độ | Hành vi |
|---|---|
| `DEFAULT` | Chỉ tự retry method an toàn như GET, HEAD và OPTIONS |
| `NEVER` | Không retry |
| `IDEMPOTENT` | Cho phép retry mutation nhưng bắt buộc có `Idempotency-Key` |

Refresh do `401` chỉ xảy ra tối đa một lần cho mỗi request. Nếu response sau refresh vẫn là `401`, framework trả
response về test thay vì tạo vòng lặp login vô hạn.

---

## 3. Luồng dọn dẹp và ghi report

`@AfterMethod(alwaysRun = true)` vẫn chạy khi test pass, fail hoặc bị skip.

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
    Note over Ledger: LIFO — resource tạo sau được xóa trước

    loop Mỗi cleanup action theo thứ tự ngược
        Ledger->>API: DELETE resource con
        API-->>Ledger: Cleanup response
    end

    alt Test đã fail và cleanup cũng fail
        Ledger-->>Core: CleanupException
        Core->>TestNG: Add cleanup error vào suppressed errors
    else Test pass nhưng cleanup fail
        Ledger-->>Core: CleanupException
        Core->>TestNG: Chuyển test result thành FAILED
    else Cleanup thành công
        Ledger-->>Core: Done
    end

    Core->>Report: write(context, status, error)
    Report->>Report: Ghi redacted JSON trace
    Core->>Holder: clear()
    Note right of Holder: Luôn xóa ThreadLocal trong finally
```

Ví dụ thứ tự resource:

```text
Tạo:  Organization A -> Employee B -> Contract C
Xóa:  Contract C     -> Employee B -> Organization A
```

Nhờ LIFO, resource con luôn được xóa trước resource cha.

---

## 4. Luồng kết thúc test class

```mermaid
sequenceDiagram
    autonumber
    participant TestNG
    participant Core as BaseApiTest
    participant Runtime as ApiTestRuntime
    participant Cred as CredentialManager
    participant Consumer as Project Base Test

    Note over TestNG,Consumer: @AfterClass — chạy 1 lần
    TestNG->>Core: finalizeApiRuntime()
    Core->>Runtime: close()
    Runtime->>Cred: clear()
    Note right of Cred: Xóa toàn bộ credential cache của test class
    Core->>Consumer: afterApiClass()
    Consumer-->>TestNG: Hoàn tất
```

---

## 5. Wiring tối thiểu ở consumer

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
api_hrm / api-map
├── config       FrameworkConfig từ environment và secret provider
├── auth         AuthProvider cho từng auth profile
├── clients      Domain clients, không gọi RestAssured trực tiếp
├── fixtures     Tạo data và đăng ký ResourceLedger cleanup
├── scenarios    Business flows dùng lại được
└── tests        Test matrix và assertions
```
