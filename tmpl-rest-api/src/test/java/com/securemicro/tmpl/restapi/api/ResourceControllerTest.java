package com.securemicro.tmpl.restapi.api;

// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-17

import com.securemicro.shared.observability.AuditTrailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Unit tests for {@link ResourceController}.
 *
 * <h2>Test strategy</h2>
 * Uses {@code @WebFluxTest} to load only the WebFlux slice (no full application
 * context, no Vault, no Keycloak). The production
 * {@code IdentitySecurityConfiguration} is replaced by {@link TestSecurityConfig}
 * which accepts mock JWTs injected via
 * {@link org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers#mockJwt()}.
 *
 * <p>{@link AuditTrailService} is provided as a {@code @MockBean} so tests can
 * verify it was invoked without requiring a real logger or MDC context.
 *
 * <h2>Scenarios covered</h2>
 * <ol>
 *   <li>(a) {@code GET /api/v1/resources} with {@code ROLE_SERVICE_USER} → HTTP 200
 *       (CONTROL: INI-04, INI-05)</li>
 *   <li>(b) {@code POST /api/v1/resources} with {@code ROLE_SERVICE_ADMIN} → HTTP 201
 *       and audit event emitted (CONTROL: INI-05, INI-17)</li>
 *   <li>(c) {@code GET /api/v1/resources} without token → HTTP 401
 *       (CONTROL: INI-04)</li>
 *   <li>(d) {@code POST /api/v1/resources} with {@code ROLE_SERVICE_USER}
 *       (insufficient role) → HTTP 403 (CONTROL: INI-05)</li>
 * </ol>
 *
 * <p>CONTROL: INI-04 INI-05 INI-17
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-17
@WebFluxTest(ResourceController.class)
@Import(TestSecurityConfig.class)
class ResourceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * {@link AuditTrailService} mock — allows verification that the audit
     * method is called with the expected arguments on POST.
     *
     * <p>CONTROL: INI-17
     */
    @MockBean
    private AuditTrailService auditTrailService;

    // ── Criterion (a): GET /api/v1/resources with ROLE_SERVICE_USER → 200 ──

    /**
     * (a) GET /api/v1/resources with ROLE_SERVICE_USER must return HTTP 200.
     *
     * <p>Verifies CONTROL: INI-04 (authenticated) and CONTROL: INI-05
     * (correct role granted access).
     */
    @Test
    @DisplayName("(a) GET /api/v1/resources with ROLE_SERVICE_USER -> 200")
    void givenServiceUserRole_whenGetResources_thenHttp200() {
        // CONTROL: INI-04 | INI-05
        webTestClient
                .mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_USER")))
                .get()
                .uri("/api/v1/resources")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resources").isArray()
                .jsonPath("$.total").isNumber();
    }

    // ── Criterion (b): POST /api/v1/resources with ROLE_SERVICE_ADMIN → 201 + audit ──

    /**
     * (b) POST /api/v1/resources with ROLE_SERVICE_ADMIN must return HTTP 201
     * and the audit trail service must be invoked exactly once with the
     * expected arguments.
     *
     * <p>Verifies CONTROL: INI-05 (ADMIN role required) and
     * CONTROL: INI-17 (audit event emitted on data modification).
     */
    @Test
    @DisplayName("(b) POST /api/v1/resources with ROLE_SERVICE_ADMIN -> 201 + audit log emitted")
    void givenServiceAdminRole_whenPostResources_thenHttp201AndAuditEmitted() {
        // CONTROL: INI-05 | INI-17
        webTestClient
                .mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_ADMIN")))
                .post()
                .uri("/api/v1/resources")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.status").isNotEmpty();

        // Verify audit trail event was emitted exactly once (CONTROL: INI-17).
        // The subject may be the mockJwt default ("user") or any non-null value —
        // we use anyString() for the subject to avoid coupling to mock JWT internals,
        // and assert the fixed fields (action, resource, result, reason) precisely.
        verify(auditTrailService, times(1)).audit(
                org.mockito.ArgumentMatchers.anyString(), // subject from JWT
                eq("CREATE"),
                eq("/api/v1/resources"),
                eq("SUCCESS"),
                isNull()
        );
    }

    // ── Unauthenticated request → 401 ──

    /**
     * GET /api/v1/resources without a token must return HTTP 401.
     *
     * <p>Verifies CONTROL: INI-04 — the security filter chain rejects
     * unauthenticated requests before they reach the controller.
     */
    @Test
    @DisplayName("GET /api/v1/resources without token -> 401")
    void givenNoToken_whenGetResources_thenHttp401() {
        // CONTROL: INI-04
        webTestClient
                .get()
                .uri("/api/v1/resources")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Insufficient role → 403 ──

    /**
     * POST /api/v1/resources with ROLE_SERVICE_USER (insufficient) must
     * return HTTP 403.
     *
     * <p>Verifies CONTROL: INI-05 — method-level security ({@code @PreAuthorize})
     * rejects authenticated principals that lack the required ADMIN role.
     */
    @Test
    @DisplayName("POST /api/v1/resources with ROLE_SERVICE_USER (insufficient) -> 403")
    void givenServiceUserRole_whenPostResources_thenHttp403() {
        // CONTROL: INI-05
        webTestClient
                .mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_USER")))
                .post()
                .uri("/api/v1/resources")
                .exchange()
                .expectStatus().isForbidden();
    }
}
