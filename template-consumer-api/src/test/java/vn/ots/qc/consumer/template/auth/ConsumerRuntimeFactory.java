package vn.ots.qc.consumer.template.auth;

import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.restassured.RestAssuredApiClient;
import vn.ots.qc.api.testng.ApiTestRuntime;
import vn.ots.qc.api.core.trace.TraceSink;
import vn.ots.qc.consumer.template.config.ConsumerConfig;

import java.util.ArrayList;
import java.util.List;

/** Standard consumer wiring. The service authProfile must match auth.profile when auth is enabled. */
public final class ConsumerRuntimeFactory {
    private ConsumerRuntimeFactory() { }

    /** Creates the configured product service so common auth flows need only configuration changes. */
    public static ApiTestRuntime createConfiguredService(String environment, String serviceName, TraceSink traceSink) {
        ConsumerConfig config = ConsumerConfig.get();
        String baseUrl = config.require("api.base-url");
        if (baseUrl.contains("replace-me")) {
            throw new IllegalStateException("Replace the api.base-url placeholder before creating the product runtime");
        }
        String mode = config.get("auth.mode", "none");
        String profile = mode.equalsIgnoreCase("none") ? "none" : config.get("auth.profile", "api");
        ServiceConfig service = ServiceConfig.builder(serviceName, baseUrl).authProfile(profile).build();
        return create(environment, service, traceSink);
    }

    /** Keeps the offline sample service and adds the configured product service when its URL is set. */
    public static ApiTestRuntime createTemplateRuntime(String environment, ServiceConfig offlineSampleService,
                                                       TraceSink traceSink) {
        ConsumerConfig config = ConsumerConfig.get();
        List<ServiceConfig> services = new ArrayList<>();
        services.add(offlineSampleService);
        String baseUrl = config.get("api.base-url", "");
        if (!baseUrl.isBlank() && !baseUrl.contains("replace-me")) {
            String mode = config.get("auth.mode", "none");
            String profile = mode.equalsIgnoreCase("none") ? "none" : config.get("auth.profile", "api");
            services.add(ServiceConfig.builder("api", baseUrl).authProfile(profile).build());
        }
        FrameworkConfig.Builder framework = FrameworkConfig.builder().environment(environment);
        services.forEach(framework::service);
        FrameworkConfig frameworkConfig = framework.build();
        CredentialManager credentials = new CredentialManager(ConfiguredAuthProvider.providers(config));
        RestAssuredApiClient client = new RestAssuredApiClient(frameworkConfig, credentials, traceSink);
        return new ApiTestRuntime(frameworkConfig, credentials, client);
    }

    public static ApiTestRuntime create(String environment, ServiceConfig service, TraceSink traceSink) {
        ConsumerConfig config = ConsumerConfig.get();
        FrameworkConfig frameworkConfig = FrameworkConfig.builder()
                .environment(environment)
                .service(service)
                .build();
        CredentialManager credentials = new CredentialManager(ConfiguredAuthProvider.providers(config));
        RestAssuredApiClient client = new RestAssuredApiClient(frameworkConfig, credentials, traceSink);
        return new ApiTestRuntime(frameworkConfig, credentials, client);
    }
}
