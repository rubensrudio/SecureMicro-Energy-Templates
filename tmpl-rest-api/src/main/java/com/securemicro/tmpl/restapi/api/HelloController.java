package com.securemicro.tmpl.restapi.api;

// CONTROL: INI-04
// CONTROL: INI-05

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebFlux placeholder controller providing a smoke-test endpoint.
 *
 * <h2>Purpose (TASK-015)</h2>
 * Provides {@code GET /api/v1/hello} as a minimally-invasive endpoint that
 * verifies end-to-end authentication and role-based authorisation are
 * functioning correctly. It is the primary target of the integration tests
 * in {@code shared-identity} that confirm the JWT validation pipeline is
 * correctly wired.
 *
 * <h2>Security controls applied</h2>
 * <ul>
 *   <li>CONTROL: INI-04 — HTTP 401 for unauthenticated requests (enforced by
 *       the security filter chain in {@code shared-identity}). The
 *       {@code @PreAuthorize} annotation adds the role guard on top.</li>
 *   <li>CONTROL: INI-05 — HTTP 403 for authenticated requests with insufficient
 *       roles. Requires {@code ROLE_SERVICE_USER}.</li>
 * </ul>
 *
 * <p>This endpoint does not perform any data modification; therefore no audit
 * trail is emitted here. Audit events are reserved for modification operations
 * (see {@link ResourceController#createResource} for that pattern).
 *
 * <p>Stack traces are never exposed in responses — that invariant is enforced
 * globally by {@code GlobalExceptionHandler} in shared-controls (RN-10).
 */
// CONTROL: INI-04
// CONTROL: INI-05
@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

    /**
     * Returns a minimal greeting to confirm the service is reachable and
     * the caller's JWT has been validated successfully.
     *
     * <p>Access is restricted to principals carrying
     * {@code ROLE_SERVICE_USER} (CONTROL: INI-05). Unauthenticated requests
     * are rejected by the security filter chain before this method is reached
     * (CONTROL: INI-04).
     *
     * <p>The response body is intentionally static — this endpoint exists only
     * to validate the authentication pipeline end-to-end. Real implementations
     * should replace this with a meaningful domain operation.
     *
     * @return a {@link Mono} containing HTTP 200 with a greeting payload
     */
    // CONTROL: INI-04
    // CONTROL: INI-05
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SERVICE_USER')")
    public Mono<ResponseEntity<Map<String, String>>> hello() {
        // Placeholder response — the static message confirms the endpoint is
        // reachable and the JWT validation pipeline is correctly configured.
        return Mono.just(ResponseEntity.ok(Map.of(
                "message", "Hello from SecureMicro Template REST API",
                "status", "authenticated"
        )));
    }
}
