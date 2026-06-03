package com.securemicro.tmpl.restapi.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator configuration for health probes and Prometheus metrics exposure.
 *
 * <p>This class centralises all Actuator-related decisions for the template:
 * <ul>
 *   <li>Health check endpoint — accessible without authentication
 *       (CONTROL: INI-08, RN-05).</li>
 *   <li>Liveness and readiness probes — Kubernetes-ready probes exposed at
 *       {@code /actuator/health/liveness} and {@code /actuator/health/readiness}
 *       (CONTROL: INI-20, RN-05).</li>
 *   <li>Prometheus metrics endpoint — exposed without authentication so that
 *       scrape agents can reach it on a dedicated network segment without
 *       bearer token overhead (CONTROL: INI-18, RN-05).</li>
 * </ul>
 *
 * <p>Security decision: {@code /actuator/health/**} and
 * {@code /actuator/prometheus} are whitelisted as public in
 * {@code IdentitySecurityConfiguration} (TASK-009).  This class does NOT
 * duplicate that rule — it only governs which endpoints are enabled and
 * what detail level the health endpoint exposes.
 *
 * <p>Detail level is set to {@code NEVER} (see {@code application.yml}) so
 * that health responses contain only the aggregate status ({@code UP} /
 * {@code DOWN}) and never internal metadata such as library versions,
 * datasource URLs or stack traces.  This prevents information disclosure
 * to unauthenticated clients (CONTROL: RN-05, RN-10).
 *
 * <p>The actual exposure rules and detail settings are declared in
 * {@code application.yml} under {@code management:} so that operators can
 * override them per environment without recompilation.  This class provides
 * only the Spring beans that are not expressible as YAML properties.
 *
 * <h2>Endpoints exposed (configured in application.yml)</h2>
 * <ul>
 *   <li>{@code GET /actuator/health}           — aggregate status</li>
 *   <li>{@code GET /actuator/health/liveness}  — liveness probe (CONTROL: INI-20)</li>
 *   <li>{@code GET /actuator/health/readiness} — readiness probe (CONTROL: INI-20)</li>
 *   <li>{@code GET /actuator/prometheus}        — Micrometer / Prometheus metrics
 *       (CONTROL: INI-18)</li>
 * </ul>
 *
 * <h2>Control annotations</h2>
 * <pre>
 * // CONTROL: RN-05   — health/readiness/liveness probes present and functional
 * // CONTROL: INI-18  — Micrometer metrics in /actuator/prometheus
 * // CONTROL: INI-20  — liveness and readiness probes
 * </pre>
 *
 * @see com.securemicro.shared.identity.IdentitySecurityConfiguration
 *      IdentitySecurityConfiguration for the permitAll() rules on health and prometheus paths
 */
// CONTROL: RN-05
// CONTROL: INI-18
// CONTROL: INI-20
@Configuration
public class ActuatorConfiguration {

    /**
     * Custom liveness health indicator for the template service.
     *
     * <p>A liveness indicator answers the question: "Is the JVM / application
     * process in a healthy state and worth keeping alive?"  The default
     * Spring Boot liveness group aggregates all indicators registered under
     * the liveness group; this bean adds a template-specific check that
     * confirms the application context wired correctly.
     *
     * <p>The indicator always returns {@code UP} in this placeholder
     * implementation.  Extending teams should replace it with a check that
     * fails if any non-recoverable internal invariant is violated (e.g.
     * critical thread pool exhausted, corrupt in-memory state).
     *
     * <p>CONTROL: INI-20 — liveness probe present and functional.
     * CONTROL: RN-05  — health probes present and functional.
     *
     * @return a {@link HealthIndicator} that reports the template service
     *         liveness status.
     */
    // CONTROL: INI-20
    // CONTROL: RN-05
    @Bean
    public HealthIndicator templateLivenessIndicator() {
        // CONTROL: RN-05
        // Returns UP; replace with real liveness logic in derived services.
        // The response body exposes only the status string — no internal
        // detail — because management.endpoint.health.show-details=never
        // is set in application.yml.
        return () -> Health.up()
                .withDetail("component", "tmpl-rest-api")
                .build();
    }

    /**
     * Custom readiness health indicator for the template service.
     *
     * <p>A readiness indicator answers the question: "Is the service ready to
     * accept traffic?"  Spring Boot's readiness group aggregates registered
     * indicators; this bean adds a template-specific check.
     *
     * <p>In this placeholder implementation the indicator always returns
     * {@code UP}.  Extending teams should replace this with a check against
     * downstream dependencies that must be available before the service can
     * process requests (e.g. database connectivity, required remote config).
     *
     * <p>CONTROL: INI-20 — readiness probe present and functional.
     * CONTROL: RN-05  — health probes present and functional.
     *
     * @return a {@link HealthIndicator} that reports the template service
     *         readiness status.
     */
    // CONTROL: INI-20
    // CONTROL: RN-05
    @Bean
    public HealthIndicator templateReadinessIndicator() {
        // CONTROL: RN-05
        // Returns UP; replace with real readiness logic in derived services.
        return () -> Health.up()
                .withDetail("component", "tmpl-rest-api")
                .build();
    }
}
