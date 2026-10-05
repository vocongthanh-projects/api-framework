package vn.ots.qc.consumer.template.client;

import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.http.ApiClient;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.ApiResponse;

import java.util.Map;

/** Consumer-owned domain client. Keep application endpoints out of framework modules. */
public final class SampleApiClient {
    private final ApiClient api;

    public SampleApiClient(ApiClient api) {
        this.api = api;
    }

    public ApiResponse health(Actor actor) {
        return api.execute(ApiRequest.get("sample", "/health").actor(actor).build());
    }

    public ApiResponse createItem(Actor actor, String name) {
        return api.execute(ApiRequest.post("sample", "/items")
                .actor(actor)
                .header("Content-Type", "application/json")
                .body(Map.of("name", name))
                .build());
    }

    public ApiResponse deleteItem(Actor actor, String id) {
        return api.execute(ApiRequest.delete("sample", "/items/" + id).actor(actor).build());
    }
}
