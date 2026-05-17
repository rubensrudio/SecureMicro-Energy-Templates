package com.securemicro.tmpl.restapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Actuator health probes and Prometheus metrics.
 *
 * <p>Verifies acceptance criteria from TASK-016:
 * <ul>
 *   <li>{@code GET /actuator/health} returns HTTP 200 without authentication
 *       (CONTROL: INI-08, RN-05).</li>
 *   <li>{@code GET /actuator/health/liveness} returns HTTP 200 without
 *       authentication (CONTROL: INI-20, RN-05).</li>
 *   <li>{@code GET /actuator/health/readiness} returns HTTP 200 without
 *       authentication (CONTROL: INI-20, RN-05).</li>
 *   <li>{@code GET /actuator/prometheus} returns HTTP 200 without authentication
 *       and the body contains {@code http_server_requests} and {@code jvm_memory}
 *       metric families (CONTROL: INI-18).</li>
 *   <li>Health response body does NOT contain sensitive detail (stack traces,
 *       library versions, datasource URLs) — only aggregate status
 *       (CONTROL: RN-05, RN-10).</li>
 * </ul>
 *
 * <h2>Test strategy</h2>
 * <p>The full Spring context is loaded with {@code @SpringBootTest(webEnvironment
 * = RANDOM_PORT)} to exercise the real Actuator servlet mapping and the real
 * Security filter chain.  A random port prevents conflicts with other processes.
 *
 * <p>Vault is disabled ({@code spring.cloud.vault.enabled=false}) so the context
 * starts without a live Vault instance.
 *
 * <p>{@code IdentitySecurityConfiguration} wires a
 * {@link ReactiveJwtDecoder} by calling
 * {@code ReactiveJwtDecoders.fromIssuerLocation(issuerUri)}, which performs a
 * live HTTP call to the issuer's OIDC discovery endpoint.  To prevent that
 * network call in tests, the {@link ReactiveJwtDecoder} bean is replaced with
 * a Mockito mock via {@code @MockBean}.  The mock is never invoked in these
 * tests because all tested paths are whitelisted as {@code permitAll()} in the
 * security filter chain.
 *
 * <p>CONTROL: RN-05 | INI-08 | INI-18 | INI-20
 */
// CONTROL: RN-05
// CONTROL: INI-08
// CONTROL: INI-18
// CONTROL: INI-20
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // Provide placeholder values so IdentitySecurityConfiguration binds
        // its @Value fields without attempting OIDC discovery.
        // The actual values are irrelevant — jwtDecoder() is mocked below.
        "KEYCLOAK_ISSUER_URI=http://localhost:9999/realms/test",
        "JWT_AUDIENCE=test",
        // Disable Vault so the context starts without a live Vault instance.
        // CONTROL: RN-01 — tests must not require real secrets infrastructure.
        "spring.cloud.vault.enabled=false",
        "spring.config.import=",
        // Override config location to use only classpath resources.
        // This prevents Spring Boot from picking up tmpl-rest-api/config/application.yml
        // (the operator-facing config file intended for production deployment, not for
        // the test classpath), which would cause a DuplicateKeyException because that
        // file has the management key split across two YAML documents.
        "spring.config.location=classpath:/",
        // Placeholder bindings to satisfy @Value in IdentitySecurityConfiguration
        "keycloak.client-secret=test-placeholder",
        "keycloak.jwt-audience=test",
        "service.api-key=test-placeholder",
        // Disable OTel tracing in tests: the opentelemetry-spring-boot-starter
        // may fail with ClassNotFoundException (EventLoggerProvider) depending
        // on the resolved OTel incubator version. Tracing is orthogonal to the
        // Actuator health/metrics endpoints being tested here.
        "management.tracing.enabled=false",
        "otel.sdk.disabled=true"
})
class ActuatorConfigurationTest {

    /**
     * Replace the real {@link ReactiveJwtDecoder} bean with a Mockito mock.
     *
     * <p>{@code IdentitySecurityConfiguration.jwtDecoder()} calls
     * {@code ReactiveJwtDecoders.fromIssuerLocation(issuerUri)}, which makes a
     * live HTTP request to {@code KEYCLOAK_ISSUER_URI/.well-known/openid-configuration}.
     * In a test environment that URL does not exist, so the context would fail to
     * load.  The {@code @MockBean} annotation replaces the real bean with a Mockito
     * mock before the context is refreshed, preventing the network call entirely.
     *
     * <p>The mock is intentionally unused in these tests — all actuator paths are
     * whitelisted as {@code permitAll()} and no JWT validation occurs.
     */
    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @Autowired
    private WebTestClient webTestClient;

    // =========================================================================
    // Health endpoint — aggregate
    // CONTROL: INI-08 | RN-05
    // =========================================================================

    /**
     * Verifies that {@code GET /actuator/health} returns HTTP 200 without any
     * Authorization header.
     *
     * <p>This test covers acceptance criterion:
     * "WHEN the health check endpoint is called THEN SHALL be accessible without
     * authentication" (INI-US-02 AC 5).
     *
     * <p>CONTROL: INI-08 — health endpoint accessible without token.
     * CONTROL: RN-05  — probe present and functional.
     */
    // CONTROL: INI-08
    // CONTROL: RN-05
    @Test
    @DisplayName("GET /actuator/health returns 200 without Authorization header")
    void health_returnsOk_withoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Verifies that the health response body contains only an aggregate status
     * and does not expose sensitive details such as library versions, datasource
     * URLs or stack traces.
     *
     * <p>{@code management.endpoint.health.show-details=never} is set in
     * {@code application.yml}.  At that setting Spring Boot emits only
     * {@code {"status":"UP"}} — no {@code components}, {@code details} or any
     * other sub-tree.
     *
     * <p>CONTROL: RN-05 — health response must not expose sensitive internal detail.
     * CONTROL: RN-10  — stack traces must never appear in responses.
     */
    // CONTROL: RN-05
    // CONTROL: RN-10
    @Test
    @DisplayName("GET /actuator/health body does not expose sensitive details")
    void health_responseBody_doesNotExposeDetails() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    // Body must contain the status field.
                    assertThat(body)
                            .as("Health response must contain 'status' field")
                            .contains("status");
                    // Body must NOT contain internal component details.
                    // show-details=never guarantees this.
                    // CONTROL: RN-05
                    assertThat(body)
                            .as("Health response must NOT expose 'components' (show-details=never)")
                            .doesNotContain("\"components\"");
                    assertThat(body)
                            .as("Health response must NOT expose 'details' (show-details=never)")
                            .doesNotContain("\"details\"");
                    // Stack traces must never appear in health responses.
                    // CONTROL: RN-10
                    assertThat(body)
                            .as("Health response must NOT contain stack trace fragments")
                            .doesNotContain("at com.");
                    assertThat(body)
                            .as("Health response must NOT contain 'stackTrace' field")
                            .doesNotContain("stackTrace");
                });
    }

    // =========================================================================
    // Liveness probe
    // CONTROL: INI-20 | RN-05
    // =========================================================================

    /**
     * Verifies that {@code GET /actuator/health/liveness} returns HTTP 200
     * without any Authorization header.
     *
     * <p>CONTROL: INI-20 — liveness probe present and accessible without auth.
     * CONTROL: RN-05  — probe present and functional.
     */
    // CONTROL: INI-20
    // CONTROL: RN-05
    @Test
    @DisplayName("GET /actuator/health/liveness returns 200 without Authorization header")
    void liveness_returnsOk_withoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    // =========================================================================
    // Readiness probe
    // CONTROL: INI-20 | RN-05
    // =========================================================================

    /**
     * Verifies that {@code GET /actuator/health/readiness} returns HTTP 200
     * without any Authorization header.
     *
     * <p>CONTROL: INI-20 — readiness probe present and accessible without auth.
     * CONTROL: RN-05  — probe present and functional.
     */
    // CONTROL: INI-20
    // CONTROL: RN-05
    @Test
    @DisplayName("GET /actuator/health/readiness returns 200 without Authorization header")
    void readiness_returnsOk_withoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }

    // =========================================================================
    // Prometheus metrics endpoint
    // CONTROL: INI-18 | RN-05
    // =========================================================================

    /**
     * Verifies that {@code GET /actuator/prometheus} returns HTTP 200 without
     * any Authorization header.
     *
     * <p>CONTROL: INI-18 — Prometheus endpoint exposed without authentication.
     * CONTROL: RN-05  — metrics endpoint present and functional.
     */
    // CONTROL: INI-18
    // CONTROL: RN-05
    @Test
    @DisplayName("GET /actuator/prometheus returns 200 without Authorization header")
    void prometheus_returnsOk_withoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Verifies that the Prometheus response body contains the
     * {@code http_server_requests} metric family.
     *
     * <p>This metric is emitted by Micrometer's Spring WebFlux instrumentation
     * for every HTTP request processed by the server.  Its presence confirms
     * that Micrometer is active and integrated with the HTTP layer.
     *
     * <p>CONTROL: INI-18 — metrics with {@code http_server_requests} prefix present.
     */
    // CONTROL: INI-18
    @Test
    @DisplayName("GET /actuator/prometheus body contains http_server_requests metrics")
    void prometheus_body_containsHttpServerRequestsMetrics() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(body)
                                .as("Prometheus body must contain 'http_server_requests' metric family")
                                .contains("http_server_requests")
                );
    }

    /**
     * Verifies that the Prometheus response body contains the
     * {@code jvm_memory} metric family.
     *
     * <p>JVM memory metrics are emitted by Micrometer's JVM instrumentation
     * ({@code JvmMemoryMetrics} binder).  Their presence confirms that the JVM
     * metric binders are active and integrated with the Prometheus registry.
     *
     * <p>CONTROL: INI-18 — metrics with {@code jvm_memory} prefix present.
     */
    // CONTROL: INI-18
    @Test
    @DisplayName("GET /actuator/prometheus body contains jvm_memory metrics")
    void prometheus_body_containsJvmMemoryMetrics() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(body)
                                .as("Prometheus body must contain 'jvm_memory' metric family")
                                .contains("jvm_memory")
                );
    }
}
