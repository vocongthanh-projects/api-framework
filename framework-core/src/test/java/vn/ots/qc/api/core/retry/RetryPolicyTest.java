package vn.ots.qc.api.core.retry;

import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.config.RetryConfig;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.RetryMode;

import java.time.Duration;

public class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy(RetryConfig.defaults());

    @Test
    public void retriesSafeMethodButNotPostByDefault() {
        ApiRequest get = ApiRequest.get("svc", "/items").build();
        ApiRequest post = ApiRequest.post("svc", "/items").build();
        Assert.assertTrue(policy.canRetry(get, 1, Duration.ZERO));
        Assert.assertFalse(policy.canRetry(post, 1, Duration.ZERO));
    }

    @Test
    public void explicitIdempotencyAllowsRetryForPost() {
        ApiRequest post = ApiRequest.post("svc", "/items")
                .header("Idempotency-Key", "request-123")
                .retryMode(RetryMode.IDEMPOTENT)
                .build();
        Assert.assertTrue(policy.canRetry(post, 1, Duration.ZERO));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Idempotency-Key.*")
    public void rejectsIdempotentModeWithoutIdempotencyKey() {
        ApiRequest.post("svc", "/items").retryMode(RetryMode.IDEMPOTENT).build();
    }
}
