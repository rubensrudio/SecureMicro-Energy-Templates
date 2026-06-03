package com.securemicro.tmpl.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/**
 * Entry point for the SecureMicro Template REST API.
 *
 * <p>This is a Spring Boot WebFlux application (reactive, non-Servlet) that
 * serves as the foundation for production-grade microservices in the US energy
 * sector. Security controls, observability, identity validation and secrets
 * management are provided by the four shared-* libraries declared as Maven
 * dependencies in {@code pom.xml}.
 *
 * <h2>Shared modules activated on startup</h2>
 * <ul>
 *   <li><b>shared-controls</b> — injects HTTP security headers (HSTS, CSP,
 *       X-Frame-Options, X-Content-Type-Options) via {@code SecurityHeadersFilter}
 *       and suppresses stack traces in error responses via
 *       {@code GlobalExceptionHandler}. CONTROL: RN-10</li>
 *   <li><b>shared-observability</b> — emits structured JSON log events per
 *       request (correlation-id, trace-id, method, path, status, duration,
 *       auth.subject) and exports Micrometer metrics at
 *       {@code /actuator/prometheus}.
 *       CONTROL: INI-16 | INI-18 | INI-19 | INI-21</li>
 *   <li><b>shared-identity</b> — configures the reactive Spring Security filter
 *       chain as an OAuth2 resource server. Validates JWT signatures against
 *       Keycloak's JWKS endpoint (resolved via {@code KEYCLOAK_ISSUER_URI} env
 *       var) and checks the {@code aud} claim (via {@code JWT_AUDIENCE}).
 *       Returns HTTP 401/403 with generic bodies — no internal detail exposed.
 *       CONTROL: INI-04 | INI-05 | INI-06 | INI-07 | RN-02</li>
 *   <li><b>shared-secrets</b> — integrates HashiCorp Vault as a ConfigData
 *       property source. Resolves KV v2 secrets before any bean that requires
 *       them is initialised. Fails fast if Vault is unreachable.
 *       Disabled via {@code spring.cloud.vault.enabled=false} for local
 *       development and test profiles without a running Vault instance.
 *       CONTROL: RN-01 | INI-11 | INI-12</li>
 * </ul>
 *
 * <h2>Runtime prerequisites</h2>
 * <p>The following environment variables must be set for the application to
 * start successfully in production:
 * <ul>
 *   <li>{@code KEYCLOAK_ISSUER_URI} — OIDC issuer URL of the Keycloak realm
 *       (e.g. {@code https://keycloak.internal/realms/energy}). CONTROL: INI-06</li>
 *   <li>{@code JWT_AUDIENCE} — expected {@code aud} claim value in JWTs issued
 *       to this service (e.g. {@code tmpl-rest-api}). CONTROL: INI-07</li>
 *   <li>{@code VAULT_ADDR} — full URL of the Vault server
 *       (e.g. {@code https://vault.internal:8200}). CONTROL: RN-01</li>
 *   <li>{@code VAULT_TOKEN} — Vault token with read access to the KV paths
 *       declared in {@code application.yml}. CONTROL: RN-01</li>
 * </ul>
 *
 * <p>For local development and CI compilation without Keycloak or Vault, set:
 * <pre>
 *   spring.cloud.vault.enabled=false
 *   KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/dev   (placeholder)
 *   JWT_AUDIENCE=tmpl-rest-api                              (placeholder)
 * </pre>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /actuator/health}            — public, no auth required</li>
 *   <li>{@code GET  /actuator/health/liveness}   — public, no auth required</li>
 *   <li>{@code GET  /actuator/health/readiness}  — public, no auth required</li>
 *   <li>{@code GET  /actuator/prometheus}         — public, no auth required</li>
 *   <li>{@code GET  /api/v1/hello}               — requires ROLE_SERVICE_USER</li>
 *   <li>{@code GET  /api/v1/resources}           — requires ROLE_SERVICE_USER</li>
 *   <li>{@code POST /api/v1/resources}           — requires ROLE_SERVICE_ADMIN</li>
 * </ul>
 * Controllers and endpoint implementations are added in TASK-015.
 *
 * @see <a href="../../../../../../control-mapping.md">Control Mapping (federal traceability)</a>
 * @see <a href="../../../../../../threat-model.md">Threat Model (STRIDE)</a>
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-06
// CONTROL: INI-07
// CONTROL: RN-01
// CONTROL: RN-02
// CONTROL: RN-10
// CONTROL: INI-05 — @EnableReactiveMethodSecurity enables @PreAuthorize role enforcement
@SpringBootApplication
@EnableReactiveMethodSecurity
public class TmplRestApiApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(TmplRestApiApplication.class, args);
    }
}
