package com.securemicro.shared.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the shared-observability module.
 *
 * <p>Bind in {@code application.yml} (or environment variables) under the
 * prefix {@code securemicro.observability}:
 *
 * <pre>{@code
 * securemicro:
 *   observability:
 *     service-name: my-service
 *     service-version: 1.0.0
 * }</pre>
 *
 * <p>These values are injected into log events and metric tags by
 * {@code RequestLoggingFilter} (TASK-006) and {@code OtelConfiguration}
 * (TASK-008).
 *
 * <p>CONTROL: INI-16 | INI-19
 */
@ConfigurationProperties(prefix = "securemicro.observability")
public class ObservabilityProperties {

    /**
     * Logical name of the service.  Appears in every structured log event
     * as {@code service.name} and as the OTel resource attribute
     * {@code service.name}.
     */
    private String serviceName = "securemicro-service";

    /**
     * Deployed version of the service.  Appears in every structured log
     * event as {@code service.version} and as the OTel resource attribute
     * {@code service.version}.
     */
    private String serviceVersion = "unknown";

    // ------------------------------------------------------------------ //
    //  Accessors                                                           //
    // ------------------------------------------------------------------ //

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }
}
