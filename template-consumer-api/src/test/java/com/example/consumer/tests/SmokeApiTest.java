package com.example.consumer.tests;

import com.example.consumer.base.BaseProjectApiTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;

public final class SmokeApiTest extends BaseProjectApiTest {
    @Test(groups = "smoke")
    public void callsTheInProcessSampleApi() {
        Actor actor = Actor.anonymous();
        var response = sampleClient().health(actor);

        response.requireStatus(200);
        Assert.assertEquals(response.json().path("status").asText(), "ok");
        Assert.assertEquals(context().traces().size(), 1);

        var created = sampleClient().createItem(actor, "template-item").requireStatus(201);
        String itemId = created.json().path("id").asText();
        resources().register("sample-item", itemId,
                () -> sampleClient().deleteItem(actor, itemId).requireStatus(200));
    }
}
