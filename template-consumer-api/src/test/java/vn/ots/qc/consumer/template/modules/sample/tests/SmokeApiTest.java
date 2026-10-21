package vn.ots.qc.consumer.template.modules.sample.tests;

import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.fixture.UniqueData;
import vn.ots.qc.consumer.template.base.BaseProjectApiTest;
import vn.ots.qc.consumer.template.modules.sample.model.CreateItemRequest;

public final class SmokeApiTest extends BaseProjectApiTest {
  @Test(groups = "smoke")
  public void callsTheInProcessSampleApi() {
    Actor actor = Actor.anonymous();
    var response = sampleClient().health(actor);

    response.requireStatus(200);
    Assert.assertEquals(response.json().path("status").asText(), "ok");
    Assert.assertEquals(context().traces().size(), 1);

    var created =
        sampleClient()
            .createItem(actor, new CreateItemRequest(UniqueData.value("template-item-")))
            .requireStatus(201);
    String itemId = created.json().path("id").asText();
    resources()
        .register(
            "sample-item",
            itemId,
            () -> sampleClient().deleteItem(actor, itemId).requireStatus(200));
  }
}
