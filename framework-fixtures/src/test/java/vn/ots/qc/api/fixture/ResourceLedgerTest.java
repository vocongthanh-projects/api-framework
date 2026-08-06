package vn.ots.qc.api.fixture;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class ResourceLedgerTest {
    @Test
    public void cleansResourcesInReverseOrder() {
        List<String> order = new ArrayList<>();
        ResourceLedger ledger = new ResourceLedger();
        ledger.register("employee", "1", () -> order.add("employee"));
        ledger.register("organization", "2", () -> order.add("organization"));
        ledger.close();
        Assert.assertEquals(order, List.of("organization", "employee"));
    }

    @Test
    public void runsRemainingCleanupAfterOneFailure() {
        List<String> cleaned = new ArrayList<>();
        ResourceLedger ledger = new ResourceLedger();
        ledger.register("first", "1", () -> cleaned.add("first"));
        ledger.register("broken", "2", () -> { throw new Exception("boom"); });
        try {
            ledger.close();
            Assert.fail("Expected cleanup failure");
        } catch (CleanupException expected) {
            Assert.assertEquals(cleaned, List.of("first"));
            Assert.assertEquals(expected.getSuppressed().length, 1);
        }
    }
}
