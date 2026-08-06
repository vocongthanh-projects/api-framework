# Test design

## Consumer layers

- **Client:** endpoint, HTTP method, serialization and deserialization only.
- **Fixture:** creates isolated data and registers cleanup immediately.
- **Scenario:** coordinates actors and several clients through a business flow.
- **Assertion:** owns domain rules such as attendance before/after deltas.
- **Test:** states intent and expected outcome.

An A-to-Z approval workflow is one test with many captured exchanges. Do not split one scenario into a chain of
`dependsOnMethods`; an early failure would turn useful downstream evidence into skipped tests.

## Example

```java
@Test(groups = "workflow")
public void employeeLeaveIsApprovedThroughEveryLevel() {
    ApprovalActors actors = fixtures.createApprovalHierarchy(resources());
    LeaveRequest leave = leaveScenario.submit(actors.employee());
    leaveScenario.approve(leave, actors.leader());
    leaveScenario.approve(leave, actors.unitLeader());
    leaveScenario.approve(leave, actors.deputy());
    leaveScenario.approve(leave, actors.head());
    leaveAssertions.isFinallyApproved(leave);
}
```

## Rules

- Register cleanup immediately after successful creation.
- Generate unique names, emails and codes using `UniqueData`.
- Replace fixed sleeps with `Eventually` and a business condition.
- Use DTOs for stable schemas; reserve maps for truly dynamic payloads.
- Assert status, contract and business state separately.
- Do not change an expected result to match a known backend bug.
- Negative tests should select `RetryMode.NEVER` when retry would distort the result.
- Destructive tests belong to a protected/manual group.

## Runtime wiring

Each consumer defines one project base class extending `BaseApiTest` and implements `createRuntime()`. Domain test
classes extend that project base class and use `api()` or consumer domain clients initialized from `runtime()`.
Per-test setup belongs in `beforeApiTest()` only when the state genuinely differs for every method.
