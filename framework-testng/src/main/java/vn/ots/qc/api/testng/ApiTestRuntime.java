package vn.ots.qc.api.testng;

import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.http.ApiClient;

import java.util.Objects;

/** Consumer wiring created once per test class, normally in one project-specific base class. */
public record ApiTestRuntime(FrameworkConfig config, CredentialManager credentials, ApiClient client)
        implements AutoCloseable {
    public ApiTestRuntime {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(client, "client");
    }

    @Override
    public void close() { credentials.clear(); }
}

