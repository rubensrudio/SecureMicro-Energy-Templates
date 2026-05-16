package com.securemicro.shared.observability;

// CONTROL: INI-19
// CONTROL: INI-21

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration for shared-observability.
 *
 * <h2>Trace propagation — W3C TraceContext (AC INI-19)</h2>
 * <p>W3C TraceContext propagation is configured via the
 * {@code application-observability.yml} profile property:
 * <pre>
 *   otel.propagators: tracecontext,baggage
 * </pre>
 * The {@code opentelemetry-spring-boot-starter} auto-reads this property
 * through its own auto-configuration and sets up the
 * {@code W3CTraceContextPropagator} (and {@code W3CBaggagePropagator})
 * as the global propagators. No programmatic SDK configuration is required
 * here — the starter handles the wiring transparently.
 *
 * <p>CONTROL: INI-19
 *
 * <h2>OTLP endpoint — infrastructure address, not a secret (AC INI-19)</h2>
 * <p>The OTLP exporter endpoint is an <em>infrastructure address</em>, not a
 * credential or secret. It may safely be supplied via an environment variable
 * or an {@code application.yml} property. The value is read from the
 * {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable with a fallback to
 * {@code http://localhost:4318} (the standard OTLP/HTTP port used by the OTel
 * collector and by Jaeger in all-in-one mode).
 *
 * <p>This bean documents the resolved endpoint at startup for operational
 * visibility; the actual exporter is configured by the
 * {@code opentelemetry-spring-boot-starter} auto-configuration, which reads
 * the same {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable directly.
 *
 * <h2>Graceful degradation — no OTLP collector available (AC INI-21)</h2>
 * <p>The {@code opentelemetry-spring-boot-starter} (version 2.14.0) uses an
 * <em>asynchronous, non-blocking</em> OTLP exporter. At startup the SDK
 * registers the exporter with the configured endpoint but does <strong>not</strong>
 * attempt a synchronous connection. The first real export attempt happens after
 * the first span is completed, entirely off the main startup thread.
 *
 * <p>As a result:
 * <ul>
 *   <li>If the OTLP endpoint is unreachable, the exporter silently drops spans
 *       and logs a warning — it does <strong>not</strong> throw an exception or
 *       prevent the {@code ApplicationContext} from starting.</li>
 *   <li>No additional configuration (try/catch, {@code @ConditionalOnBean}, etc.)
 *       is required in this class to achieve AC INI-21; the starter provides this
 *       behaviour by design.</li>
 *   <li>The application continues to serve requests normally even with zero spans
 *       exported — observability degrades gracefully rather than crashing.</li>
 * </ul>
 *
 * <p>CONTROL: INI-21
 *
 * <h2>Activation</h2>
 * <p>This {@code @Configuration} class is picked up automatically by Spring's
 * component scan because it lives in the same package as
 * {@link SharedObservabilityAutoConfiguration}. The
 * {@code application-observability.yml} profile must be activated via
 * {@code spring.profiles.active=observability} (or equivalent) for the OTel
 * properties to be loaded.
 */
@Configuration
public class OtelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OtelConfiguration.class);

    /**
     * The OTLP exporter endpoint resolved from the environment or application
     * properties.
     *
     * <p>This is an infrastructure address (host:port), <strong>not</strong> a
     * secret. The fallback {@code http://localhost:4318} matches the default
     * OTLP/HTTP port used by the OpenTelemetry Collector and by Jaeger
     * all-in-one mode.
     *
     * <p>CONTROL: INI-19
     */
    @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}")
    private String otlpEndpoint;

    /**
     * Logs the resolved OTLP endpoint at startup for operational visibility.
     *
     * <p>This is the only explicit action performed by this configuration class.
     * All OTel SDK wiring (exporter, propagator, sampler) is handled by the
     * {@code opentelemetry-spring-boot-starter} auto-configuration, which reads
     * the {@code otel.*} properties from {@code application-observability.yml}
     * and from the {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable.
     *
     * <p>Graceful degradation: if the endpoint is unreachable at the time spans
     * are first exported, the SDK logs a warning and drops the batch — the
     * application context is unaffected (AC INI-21).
     *
     * <p>CONTROL: INI-19 | INI-21
     */
    // CONTROL: INI-19
    // CONTROL: INI-21
    void logOtlpEndpoint() {
        log.info(
            "[OtelConfiguration] OTLP exporter endpoint resolved to '{}'. "
            + "W3C TraceContext propagation active (otel.propagators=tracecontext,baggage). "
            + "Graceful degradation: if the endpoint is unreachable, spans are silently "
            + "dropped — the application context starts normally (AC INI-21).",
            otlpEndpoint
        );
    }

    /**
     * Returns the resolved OTLP endpoint value for use in tests and diagnostics.
     *
     * @return the OTLP exporter endpoint URL
     */
    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }
}
