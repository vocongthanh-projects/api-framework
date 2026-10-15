package vn.ots.qc.consumer.template.tests;

import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.consumer.template.base.BaseProjectApiTest;
import vn.ots.qc.consumer.template.data.CsvDataProvider;

public final class CsvDataDrivenApiTest extends BaseProjectApiTest {
  @Test(dataProvider = "sampleItems", dataProviderClass = CsvDataProvider.class, groups = "smoke")
  public void createsItemsFromCsv(String caseName, String itemName, int expectedStatus) {
    var created =
        sampleClient().createItem(Actor.anonymous(), itemName).requireStatus(expectedStatus);
    String itemId = created.json().path("id").asText();
    Assert.assertFalse(itemId.isBlank(), "case: " + caseName);
    resources()
        .register(
            "sample-item-" + caseName,
            itemId,
            () -> sampleClient().deleteItem(Actor.anonymous(), itemId).requireStatus(200));
  }
}
