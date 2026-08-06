package vn.ots.qc.api.core.http;

public enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

    public boolean safeToRetry() {
        return this == GET || this == HEAD || this == OPTIONS;
    }
}

