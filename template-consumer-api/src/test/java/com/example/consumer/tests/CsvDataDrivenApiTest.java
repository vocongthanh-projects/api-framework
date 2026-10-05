package com.example.consumer.tests;

import com.example.consumer.base.BaseProjectApiTest;
import com.example.consumer.data.CsvDataProvider;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;

public final class CsvDataDrivenApiTest extends BaseProjectApiTest {
    @Test(dataProvider = "sampleItems", dataProviderClass = CsvDataProvider.class, groups = "smoke")
    public void createsItemsFromCsv(String caseName, String itemName, int expectedStatus) {
        var created = sampleClient().createItem(Actor.anonymous(), itemName).requireStatus(expectedStatus);
        String itemId = created.json().path("id").asText();
        Assert.assertFalse(itemId.isBlank(), "case: " + caseName);
        resources().register("sample-item-" + caseName, itemId,
                () -> sampleClient().deleteItem(Actor.anonymous(), itemId).requireStatus(200));
    }
}
