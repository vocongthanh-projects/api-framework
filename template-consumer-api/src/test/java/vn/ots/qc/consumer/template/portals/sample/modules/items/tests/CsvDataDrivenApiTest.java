package vn.ots.qc.consumer.template.portals.sample.modules.items.tests;

import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.fixture.UniqueData;
import vn.ots.qc.consumer.template.base.BaseProjectApiTest;
import vn.ots.qc.consumer.template.data.CsvDataProvider;
import vn.ots.qc.consumer.template.portals.sample.modules.items.model.items.CreateItemRequest;

public final class CsvDataDrivenApiTest extends BaseProjectApiTest {
  @Test(dataProvider = "sampleItems", dataProviderClass = CsvDataProvider.class, groups = "smoke")
  public void createsItemsFromCsv(String caseName, String itemName, int expectedStatus) {
    var created =
        sampleClient()
            .createItem(Actor.anonymous(), new CreateItemRequest(UniqueData.value(itemName + "-")))
            .requireStatus(expectedStatus);
    String itemId = created.json().path("id").asText();
    Assert.assertFalse(itemId.isBlank(), "case: " + caseName);
    resources()
        .register(
            "sample-item-" + caseName,
            itemId,
            () -> sampleClient().deleteItem(Actor.anonymous(), itemId).requireStatus(200));
  }
}
