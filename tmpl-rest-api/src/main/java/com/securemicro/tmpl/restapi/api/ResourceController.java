package com.securemicro.tmpl.restapi.api;

// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-17

import com.securemicro.shared.observability.AuditTrailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * WebFlux placeholder controller for resource management endpoints.
 *
 * <h2>Purpose (TASK-015)</h2>
 * Demonstrates the role-based access control pattern (INI-04, INI-05) and
 * the audit trail integration (INI-17) required by all production endpoints
 * that perform data-modification operations.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/v1/resources} — requires {@code ROLE_SERVICE_USER}
 *       (CONTROL: INI-04, INI-05). Returns a placeholder list of resources.</li>
 *   <li>{@code POST /api/v1/resources} — requires {@code ROLE_SERVICE_ADMIN}
 *       (CONTROL: INI-04, INI-05). Creates a placeholder resource and emits
 *       an audit event via {@link AuditTrailService} (CONTROL: INI-17).</li>
 * </ul>
 *
 * <h2>Audit trail (CONTROL: INI-17)</h2>
 * The POST endpoint injects {@link AuditTrailService} to emit a structured
 * audit event upon every successful creation attempt. The event records:
 * subject (JWT sub claim), action, resource path, and result. This satisfies
 * the requirement that every data-modification action produces an immutable
 * audit log entry (INI-17, RN-04).
 *
 * <h2>Security controls applied</h2>
 * <ul>
 *   <li>CONTROL: INI-04 — HTTP 401 for unauthenticated requests (enforced by
 *       the security filter chain in shared-identity; the {@code @PreAuthorize}
 *       annotation adds the role guard layer on top).</li>
 *   <li>CONTROL: INI-05 — HTTP 403 for authenticated requests with insufficient
 *       roles. Method-level security via {@code @PreAuthorize}.</li>
 *   <li>CONTROL: INI-17 — Audit trail emitted on POST (data modification).</li>
 * </ul>
 *
 * <p>Stack traces are never exposed in responses — that invariant is enforced
 * globally by {@code GlobalExceptionHandler} in shared-controls (RN-10).
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-17
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final AuditTrailService auditTrailService;

    /**
     * Constructor injection of {@link AuditTrailService}.
     *
     * <p>Spring resolves this bean from the shared-observability auto-configuration.
     * In unit tests this dependency is provided by {@code @MockBean}.
     *
     * @param auditTrailService the audit trail service (never {@code null})
     */
    // CONTROL: INI-17
    public ResourceController(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    /**
     * Returns a placeholder list of energy resources.
     *
     * <p>Access is restricted to principals carrying
     * {@code ROLE_SERVICE_USER} (CONTROL: INI-05). Unauthenticated requests
     * are rejected by the security filter chain before this method is reached
     * (CONTROL: INI-04).
     *
     * <p>The response is intentionally minimal — this is a template placeholder.
     * Real implementations should replace the hardcoded list with a reactive
     * repository call and validate/sanitise all inputs.
     *
     * @return a {@link Mono} containing HTTP 200 with a placeholder resource list
     */
    // CONTROL: INI-04
    // CONTROL: INI-05
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SERVICE_USER')")
    public Mono<ResponseEntity<Map<String, Object>>> getResources() {
        // Placeholder response — real implementations replace this with
        // reactive repository calls and proper domain model objects.
        Map<String, Object> body = Map.of(
                "resources", List.of(
                        Map.of("id", "res-001", "name", "Grid Sensor Alpha", "status", "ACTIVE"),
                        Map.of("id", "res-002", "name", "Substation Monitor B", "status", "ACTIVE")
                ),
                "total", 2
        );
        return Mono.just(ResponseEntity.ok(body));
    }

    /**
     * Creates a placeholder energy resource and emits an audit trail event.
     *
     * <p>Access is restricted to principals carrying
     * {@code ROLE_SERVICE_ADMIN} (CONTROL: INI-05). Unauthenticated requests
     * are rejected by the security filter chain before this method is reached
     * (CONTROL: INI-04).
     *
     * <p>The audit event records: the JWT {@code sub} claim as the acting
     * subject, the action {@code "CREATE"}, the resource path, and the result
     * {@code "SUCCESS"} (CONTROL: INI-17). This call is the minimum required
     * to satisfy AC INI-17 — real implementations should also audit failures
     * in catch/error handlers.
     *
     * @param jwt the authenticated principal's JWT (injected by Spring Security)
     * @return a {@link Mono} containing HTTP 201 with the created resource placeholder
     */
    // CONTROL: INI-04
    // CONTROL: INI-05
    // CONTROL: INI-17
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SERVICE_ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> createResource(
            @AuthenticationPrincipal Jwt jwt) {

        // Extract the subject from the JWT for the audit record.
        // Falls back to "anonymous" if no JWT is present (should not happen
        // due to @PreAuthorize, but defensive coding prevents NPE).
        // CONTROL: INI-17
        String subject = (jwt != null) ? jwt.getSubject() : "anonymous";

        // Emit audit trail event — CONTROL: INI-17
        // Parameters: subject, action, resource, result, reason
        auditTrailService.audit(
                subject,
                "CREATE",
                "/api/v1/resources",
                "SUCCESS",
                null
        );

        // Placeholder created resource — real implementations persist via
        // reactive repository and return the persisted entity with its
        // generated ID. HTTP 201 Created is the correct status for POST
        // endpoints that create a new resource.
        Map<String, Object> created = Map.of(
                "id", "res-new-" + System.currentTimeMillis(),
                "name", "New Placeholder Resource",
                "status", "PENDING"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(created));
    }
}
