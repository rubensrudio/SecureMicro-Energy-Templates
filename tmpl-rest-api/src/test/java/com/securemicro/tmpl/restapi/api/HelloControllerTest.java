package com.securemicro.tmpl.restapi.api;

// CONTROL: INI-04
// CONTROL: INI-05

import com.securemicro.shared.observability.AuditTrailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Unit tests for {@link HelloController}.
 *
 * <h2>Test strategy</h2>
 * Uses {@code @WebFluxTest} to load only the WebFlux slice. Security is
 * provided by {@link TestSecurityConfig} with mock JWT support so no live
 * Keycloak or Vault is required.
 *
 * <p>Although {@link HelloController} itself does not inject
 * {@link AuditTrailService}, the {@code @MockBean} declaration is needed here
 * because {@link TestSecurityConfig} (imported via {@code @Import}) triggers
 * component scan boundaries that can surface the auto-configured
 * {@code AuditTrailService} bean — mocking it prevents context failures when
 * the shared-observability auto-config is on the classpath.
 *
 * <h2>Scenarios covered</h2>
 * <ol>
 *   <li>(c) {@code GET /api/v1/hello} with {@code ROLE_SERVICE_USER} → HTTP 200
 *       (CONTROL: INI-04, INI-05)</li>
 *   <li>GET /api/v1/hello without token → HTTP 401 (CONTROL: INI-04)</li>
 *   <li>GET /api/v1/hello with {@code ROLE_SERVICE_ADMIN} only → HTTP 403,
 *       proving the role guard is precise (CONTROL: INI-05)</li>
 * </ol>
 *
 * <p>CONTROL: INI-04 INI-05
 */
// CONTROL: INI-04
// CONTROL: INI-05
@WebFluxTest(HelloController.class)
@Import(TestSecurityConfig.class)
class HelloControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Declared as {@code @MockBean} to prevent auto-configuration of
     * {@link AuditTrailService} from failing the test context when
     * shared-observability is on the classpath.
     */
    @MockBean
    private AuditTrailService auditTrailService;

    // ── Criterion (c): GET /api/v1/hello with ROLE_SERVICE_USER → 200 ──

    /**
     * (c) GET /api/v1/hello with ROLE_SERVICE_USER must return HTTP 200
     * with the expected greeting payload.
     *
     * <p>Verifies CONTROL: INI-04 (authenticated) and CONTROL: INI-05
     * (correct role granted access).
     */
    @Test
    @DisplayName("(c) GET /api/v1/hello with ROLE_SERVICE_USER -> 200")
    void givenServiceUserRole_whenGetHello_thenHttp200() {
        // CONTROL: INI-04 | INI-05
        webTestClient
                .mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_USER")))
                .get()
                .uri("/api/v1/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.status").isEqualTo("authenticated");
    }

    // ── Unauthenticated request → 401 ──

    /**
     * GET /api/v1/hello without a token must return HTTP 401.
     *
     * <p>Verifies CONTROL: INI-04 — the security filter chain rejects
     * unauthenticated requests before they reach the controller.
     */
    @Test
    @DisplayName("GET /api/v1/hello without token -> 401")
    void givenNoToken_whenGetHello_thenHttp401() {
        // CONTROL: INI-04
        webTestClient
                .get()
                .uri("/api/v1/hello")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Wrong role → 403 ──

    /**
     * GET /api/v1/hello with only ROLE_SERVICE_ADMIN must return HTTP 403,
     * proving the {@code @PreAuthorize} guard requires the exact
     * {@code ROLE_SERVICE_USER} authority.
     *
     * <p>Verifies CONTROL: INI-05 — method-level security rejects principals
     * that do not carry the precise required role.
     */
    @Test
    @DisplayName("GET /api/v1/hello with ROLE_SERVICE_ADMIN only -> 403")
    void givenOnlyAdminRole_whenGetHello_thenHttp403() {
        // CONTROL: INI-05
        // ROLE_SERVICE_ADMIN alone should not satisfy a USER-only endpoint.
        webTestClient
                .mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_ADMIN")))
                .get()
                .uri("/api/v1/hello")
                .exchange()
                .expectStatus().isForbidden();
    }
}
