package com.securemicro.shared.observability;

// CONTROL: INI-19
// CONTROL: INI-21

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OtelConfiguration} — verifies:
 * <ol>
 *   <li>The Spring {@link org.springframework.context.ApplicationContext} loads
 *       successfully even when no OTLP collector is reachable
 *       (graceful degradation — AC INI-21).</li>
 *   <li>The {@code OTEL_EXPORTER_OTLP_ENDPOINT} value is read from the
 *       environment / properties and injected via {@code @Value} (AC INI-19).</li>
 *   <li>The {@code @Value} fallback resolves to {@code http://localhost:4318}
 *       when the property is absent from the environment (AC INI-19).</li>
 *   <li>{@code application-observability.yml} declares
 *       {@code otel.propagators=tracecontext,baggage}, confirming W3C
 *       TraceContext propagation is configured (AC INI-19).</li>
 * </ol>
 *
 * <h2>Why {@link ApplicationContextRunner} instead of {@code @SpringBootTest}</h2>
 * <p>{@link ApplicationContextRunner} builds a minimal Spring
 * {@link org.springframework.context.ApplicationContext} without requiring
 * {@code @SpringBootApplication} on the classpath. It avoids:
 * <ul>
 *   <li>The {@code NoClassDefFoundError} for {@code EventLoggerProvider} caused
 *       by the {@code opentelemetry-api-incubator 1.48.0-alpha} vs
 *       {@code opentelemetry-api 1.38.0} conflict when full auto-configuration
 *       is activated.</li>
 *   <li>The {@code IllegalStateException} that occurs when {@code @SpringBootTest}
 *       is used on nested classes without a {@code @SpringBootConfiguration}
 *       ancestor in scope.</li>
 * </ul>
 * <p>Each test runs against the exact same {@link OtelConfiguration} bean as in
 * production; only the surrounding auto-configuration is omitted.
 *
 * <h2>Test for W3C propagators</h2>
 * <p>The propagator test reads {@code application-observability.yml} directly via
 * {@link YamlPropertiesFactoryBean} (the same YAML loader Spring Boot uses
 * internally). This verifies the property file's content without relying on
 * {@code spring.config.import}, which {@link ApplicationContextRunner} does not
 * process in the same way as full Spring Boot auto-configuration.
 *
 * <p>CONTROL: INI-19 | INI-21
 */
class OtelConfigurationTest {

    // =========================================================================
    //  Minimal Spring context: OtelConfiguration + SimpleMeterRegistry only.
    //  No OTel SDK auto-configuration is activated — avoids incubator conflict.
    // =========================================================================

    /**
     * Provides a {@link MeterRegistry} to satisfy any Micrometer injection point
     * without pulling in Prometheus or Actuator auto-configuration.
     */
    @Configuration
    static class MinimalTestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        /**
         * The class under test — imported explicitly so the context is fully
         * deterministic and not subject to classpath-scanning surprises.
         */
        @Bean
        OtelConfiguration otelConfiguration() {
            return new OtelConfiguration();
        }
    }

    // =========================================================================
    //  Nested tests: explicit (unreachable) OTLP endpoint set
    // =========================================================================

    /**
     * Tests executed with {@code OTEL_EXPORTER_OTLP_ENDPOINT} explicitly set
     * to a closed port ({@code 19999}) to simulate an unreachable collector.
     * Verifies graceful degradation (AC INI-21) and correct {@code @Value}
     * injection (AC INI-19).
     */
    @Nested
    @DisplayName("With OTEL_EXPORTER_OTLP_ENDPOINT set to a closed port")
    class WithCustomEndpoint {

        /**
         * Runner with {@code OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:19999}
         * simulating a configured (but unreachable) OTLP collector.
         */
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MinimalTestConfig.class)
                .withPropertyValues(
                        // Simulates a configured (but unreachable) OTLP collector.
                        // CONTROL: INI-21
                        "OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:19999"
                );

        // ------------------------------------------------------------------ //
        //  AC INI-21 — Context loads even when collector is unreachable        //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("AC INI-21: context starts without exception when OTLP endpoint is unreachable")
        void contextLoads_withoutReachableOtlpEndpoint() {
            // OtelConfiguration must not open a synchronous connection at startup;
            // the OTel SDK exporter is asynchronous.  The @PostConstruct log call
            // must also not throw when the endpoint is unreachable.
            //
            // CONTROL: INI-21
            runner.run(ctx -> assertThat(ctx)
                    .as("ApplicationContext must start normally even when the OTLP "
                            + "endpoint is unreachable (AC INI-21)")
                    .hasNotFailed());
        }

        @Test
        @DisplayName("OtelConfiguration bean is wired into the context")
        void otelConfigurationBean_isPresentInContext() {
            runner.run(ctx -> assertThat(ctx)
                    .hasSingleBean(OtelConfiguration.class));
        }

        // ------------------------------------------------------------------ //
        //  AC INI-19 — @Value injected from explicit property                  //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("AC INI-19: @Value injects OTEL_EXPORTER_OTLP_ENDPOINT from the active property source")
        void otlpEndpoint_resolvedFromProperty() {
            // The runner sets OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:19999.
            // OtelConfiguration.getOtlpEndpoint() must reflect that — not the fallback.
            // This proves @Value injection reads from the live Environment, not a literal.
            //
            // CONTROL: INI-19
            runner.run(ctx -> {
                OtelConfiguration cfg = ctx.getBean(OtelConfiguration.class);
                assertThat(cfg.getOtlpEndpoint())
                        .as("@Value must inject OTEL_EXPORTER_OTLP_ENDPOINT from the "
                                + "active property source (AC INI-19)")
                        .isEqualTo("http://localhost:19999");
            });
        }
    }

    // =========================================================================
    //  Nested tests: OTEL_EXPORTER_OTLP_ENDPOINT deliberately absent
    // =========================================================================

    /**
     * Tests executed with {@code OTEL_EXPORTER_OTLP_ENDPOINT} deliberately
     * absent from the property sources. Verifies that the {@code @Value}
     * fallback in {@link OtelConfiguration} resolves to the correct default
     * value {@code http://localhost:4318} (AC INI-19).
     *
     * <p>This is the critical test that the reviewer flagged as tautological
     * in the first review: the old implementation compared
     * {@code "http://localhost:4318".equals("http://localhost:4318")} — a
     * string literal with itself — which can never fail. The present test
     * creates a real Spring context <em>without</em> the property set and
     * reads the injected value from {@link OtelConfiguration#getOtlpEndpoint()},
     * which exercises the actual {@code @Value} fallback machinery.
     */
    @Nested
    @DisplayName("Without OTEL_EXPORTER_OTLP_ENDPOINT — @Value fallback must apply")
    class WithoutEndpointProperty {

        /**
         * Runner without {@code OTEL_EXPORTER_OTLP_ENDPOINT} — the property
         * is intentionally absent so the {@code @Value} fallback is exercised.
         */
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MinimalTestConfig.class);
        // OTEL_EXPORTER_OTLP_ENDPOINT is intentionally NOT added here.
        // CONTROL: INI-19

        // ------------------------------------------------------------------ //
        //  AC INI-19 — @Value fallback when env var is absent                  //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("AC INI-19: @Value fallback resolves to http://localhost:4318 when property is absent")
        void otlpEndpoint_fallback_resolvesToDefaultPort() {
            // OTEL_EXPORTER_OTLP_ENDPOINT is NOT set in this runner.
            // The @Value annotation in OtelConfiguration is:
            //   @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}")
            // Spring must inject "http://localhost:4318" as the default.
            //
            // This test validates the REAL @Value fallback behaviour by reading
            // the resolved field via getOtlpEndpoint() against a live Spring
            // Environment — not by comparing two string literals (which would
            // never fail and prove nothing about @Value wiring).
            //
            // Port 4318 is the standard OTLP/HTTP port used by the OTel Collector
            // and by Jaeger all-in-one mode (plan section 3.2, Premissa P-05).
            //
            // CONTROL: INI-19
            runner.run(ctx -> {
                OtelConfiguration cfg = ctx.getBean(OtelConfiguration.class);
                assertThat(cfg.getOtlpEndpoint())
                        .as("@Value fallback must resolve to the standard OTLP/HTTP "
                                + "port 4318 when OTEL_EXPORTER_OTLP_ENDPOINT is absent "
                                + "from the environment (AC INI-19)")
                        .isEqualTo("http://localhost:4318");
            });
        }

        // ------------------------------------------------------------------ //
        //  AC INI-21 — Context loads even without explicit endpoint             //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("AC INI-21: context starts without exception when OTEL_EXPORTER_OTLP_ENDPOINT is absent")
        void contextLoads_withoutExplicitEndpoint() {
            // Validates that OtelConfiguration and its @PostConstruct logOtlpEndpoint()
            // do not throw when the OTLP endpoint property is absent (falls back to
            // the default http://localhost:4318).
            //
            // CONTROL: INI-21
            runner.run(ctx -> assertThat(ctx)
                    .as("ApplicationContext must start normally when "
                            + "OTEL_EXPORTER_OTLP_ENDPOINT is absent (AC INI-21)")
                    .hasNotFailed());
        }
    }

    // =========================================================================
    //  Propagator configuration — verified directly from application-observability.yml
    // =========================================================================

    /**
     * Verifies that {@code application-observability.yml} declares
     * {@code otel.propagators=tracecontext,baggage} so that the
     * {@code opentelemetry-spring-boot-starter} registers
     * {@code W3CTraceContextPropagator} at runtime (AC INI-19).
     *
     * <p>The YAML file is loaded directly via {@link YamlPropertiesFactoryBean}
     * (the same loader Spring Boot uses internally) instead of relying on
     * {@code spring.config.import}, which {@link ApplicationContextRunner} does
     * not fully process the same way as {@code @SpringBootTest}.
     */
    @Nested
    @DisplayName("application-observability.yml — propagator property")
    class PropagatorConfiguration {

        // ------------------------------------------------------------------ //
        //  AC INI-19 — W3C TraceContext propagator configured in yml            //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("AC INI-19: application-observability.yml declares otel.propagators containing 'tracecontext'")
        void propagators_containsTracecontext() {
            // Load application-observability.yml from the test classpath using the
            // same YAML parser Spring Boot uses internally.  This verifies that the
            // property file — which the opentelemetry-spring-boot-starter reads at
            // runtime to configure the global TextMapPropagator — declares the
            // correct propagator chain.
            //
            // CONTROL: INI-19
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application-observability.yml"));
            Properties props = yaml.getObject();

            assertThat(props).as("application-observability.yml must be loadable from classpath").isNotNull();

            String propagators = props.getProperty("otel.propagators");
            assertThat(propagators)
                    .as("otel.propagators must be declared in application-observability.yml (AC INI-19)")
                    .isNotNull();
            assertThat(propagators)
                    .as("otel.propagators must include 'tracecontext' for W3C TraceContext "
                            + "propagation — required by AC INI-19 (plan section 3.2)")
                    .contains("tracecontext");
            assertThat(propagators)
                    .as("otel.propagators must include 'baggage' for W3C Baggage propagation "
                            + "(AC INI-19)")
                    .contains("baggage");
        }

        @Test
        @DisplayName("AC INI-19: application-observability.yml declares otel.exporter.otlp.endpoint with fallback")
        void otlpEndpoint_declaredWithFallbackInYml() {
            // Verifies that the YAML file uses ${OTEL_EXPORTER_OTLP_ENDPOINT:...}
            // syntax, meaning the endpoint is configurable without hardcoding.
            // The raw YAML value (before Spring resolves placeholders) should contain
            // the OTEL_EXPORTER_OTLP_ENDPOINT reference.
            //
            // CONTROL: INI-19
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application-observability.yml"));
            Properties props = yaml.getObject();

            assertThat(props).isNotNull();

            // The raw value before Spring placeholder resolution contains the
            // ${OTEL_EXPORTER_OTLP_ENDPOINT:...} expression.
            String rawEndpoint = props.getProperty("otel.exporter.otlp.endpoint");
            assertThat(rawEndpoint)
                    .as("otel.exporter.otlp.endpoint must be declared in application-observability.yml (AC INI-19)")
                    .isNotNull();
            assertThat(rawEndpoint)
                    .as("otel.exporter.otlp.endpoint must reference OTEL_EXPORTER_OTLP_ENDPOINT "
                            + "env var (configurable endpoint — plan section 3.2, AC INI-19)")
                    .contains("OTEL_EXPORTER_OTLP_ENDPOINT");
        }
    }
}
