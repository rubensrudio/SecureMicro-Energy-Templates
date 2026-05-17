package com.securemicro.shared.observability;

// CONTROL: INI-16

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

// CONTROL: INI-17

/**
 * Spring Boot auto-configuration for shared observability infrastructure.
 *
 * <p>This class is registered as a Spring Boot AutoConfiguration via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * and is therefore applied automatically to any Spring Boot application
 * that declares {@code shared-observability} as a Maven dependency.
 *
 * <p>Responsibilities (TASK-005 + TASK-006 scope):
 * <ul>
 *   <li>Bootstrap the auto-configuration entry point for the module.</li>
 *   <li>Declare the {@link ObservabilityProperties} bean for
 *       per-service customisation of log fields and metric tags.</li>
 *   <li>Declare the {@link RequestLoggingFilter} bean that generates or
 *       propagates {@code X-Correlation-ID}, extracts the authenticated
 *       subject, and emits a structured JSON log event on every HTTP
 *       exchange completion (TASK-006).</li>
 * </ul>
 *
 * <p>Later tasks extend this module with:
 * <ul>
 *   <li>TASK-007: {@code AuditTrailService}</li>
 *   <li>TASK-008: {@code OtelConfiguration} + {@code application-observability.yml}</li>
 * </ul>
 *
 * <h2>Graceful degradation — AC INI-21</h2>
 * OpenTelemetry SDK (included via {@code opentelemetry-spring-boot-starter}) is
 * designed to degrade gracefully when no OTLP exporter endpoint is reachable:
 * <ol>
 *   <li>At startup the SDK registers an OTLP gRPC/HTTP exporter pointing to
 *       {@code OTEL_EXPORTER_OTLP_ENDPOINT} (default {@code localhost:4317}).</li>
 *   <li>If the endpoint is unreachable, the exporter silently drops spans — it
 *       does <strong>not</strong> throw an exception or block the
 *       {@code ApplicationContext} from starting.</li>
 *   <li>No additional configuration is required here to achieve this behaviour;
 *       it is the default mode of the OTel Java SDK.</li>
 * </ol>
 * Therefore this auto-configuration intentionally does <em>not</em> declare any
 * OTel exporter bean as a required {@code @Bean} — the SDK manages its own
 * lifecycle outside the Spring bean graph.
 *
 * <p>CONTROL: INI-16 | INI-17 | INI-18 | INI-19 | INI-21
 */
@AutoConfiguration(
        after = {
                MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class
        }
)
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class SharedObservabilityAutoConfiguration {

    /**
     * Declares the {@link AuditTrailService} as a Spring-managed bean.
     *
     * <p>The {@code @ConditionalOnMissingBean} guard lets consuming services
     * override the default implementation by declaring their own
     * {@link AuditTrailService} bean — consistent with the Spring Boot
     * auto-configuration contract.
     *
     * <p>CONTROL: INI-17
     *
     * @return the default {@link AuditTrailService}
     */
    @Bean
    @ConditionalOnMissingBean(AuditTrailService.class)
    public AuditTrailService auditTrailService() {
        return new AuditTrailService();
    }

    /**
     * Declares the {@link RequestLoggingFilter} as a Spring-managed
     * {@link WebFilter} bean.
     *
     * <p>The filter is applied to every HTTP exchange and is responsible for:
     * <ul>
     *   <li>Generating or propagating {@code X-Correlation-ID}.</li>
     *   <li>Extracting the authenticated subject from the
     *       {@link org.springframework.security.core.context.ReactiveSecurityContextHolder}.</li>
     *   <li>Emitting the structured JSON log event on exchange completion.</li>
     * </ul>
     *
     * <p>The {@code @ConditionalOnMissingBean(WebFilter.class)} guard is
     * intentionally omitted here because multiple {@code WebFilter} beans are
     * expected in a WebFlux application (security filter chain, CORS, etc.).
     * Services that need to suppress this filter can exclude the auto-configuration
     * class via {@code spring.autoconfigure.exclude}.
     *
     * <p>CONTROL: INI-16
     *
     * @param properties the resolved observability configuration
     * @return the configured {@link RequestLoggingFilter}
     */
    @Bean
    @ConditionalOnMissingBean(RequestLoggingFilter.class)
    public RequestLoggingFilter requestLoggingFilter(ObservabilityProperties properties) {
        return new RequestLoggingFilter(properties);
    }
}
