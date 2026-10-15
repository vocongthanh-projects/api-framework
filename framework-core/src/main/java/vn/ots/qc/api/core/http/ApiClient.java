package vn.ots.qc.api.core.http;

@FunctionalInterface
public interface ApiClient {
  ApiResponse execute(ApiRequest request);
}
