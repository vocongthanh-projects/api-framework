package vn.ots.qc.api.example;

import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.http.ApiClient;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.ApiResponse;

import java.util.Map;

/** Example of a consumer-owned domain client. Framework modules must not contain this class. */
public final class ItemClient {
    private final ApiClient api;

    public ItemClient(ApiClient api) { this.api = api; }

    public ApiResponse list(Actor actor) {
        return api.execute(ApiRequest.get("catalog", "/items").actor(actor).build());
    }

    public ApiResponse create(Actor actor, String name) {
        return api.execute(ApiRequest.post("catalog", "/items")
                .actor(actor)
                .header("Content-Type", "application/json")
                .body(Map.of("name", name))
                .build());
    }
}

