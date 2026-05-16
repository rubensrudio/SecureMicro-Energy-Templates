package com.securemicro.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for shared observability infrastructure.
 *
 * <p>This class is registered as a Spring Boot AutoConfiguration via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * and is therefore applied automatically to any Spring Boot application
 * that declares {@code shared-observability} as a Maven dependency.
 *
 * <p>Responsibilities (TASK-005 scope — base scaffold):
 * <ul>
 *   <li>Bootstrap the auto-configuration entry point for the module.</li>
 *   <li>Declare a placeholder {@link ObservabilityProperties} bean for
 *       per-service customisation of log fields and metric tags.</li>
 * </ul>
 *
 * <p>Later tasks extend this module with:
 * <ul>
 *   <li>TASK-006: {@code RequestLoggingFilter} + {@code logback-spring.xml}</li>
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
public class SharedObservabilityAutoConfiguration {

    /**
     * Exposes the {@link ObservabilityProperties} configuration bean.
     *
     * <p>The {@code @ConditionalOnMissingBean} guard allows individual services
     * to override the default properties by declaring their own
     * {@code ObservabilityProperties} bean — consistent with the Spring Boot
     * auto-configuration contract.
     *
     * @return a default {@link ObservabilityProperties} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservabilityProperties observabilityProperties() {
        return new ObservabilityProperties();
    }
}
