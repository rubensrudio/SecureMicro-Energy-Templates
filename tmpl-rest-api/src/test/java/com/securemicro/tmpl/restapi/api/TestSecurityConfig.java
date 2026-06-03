package com.securemicro.tmpl.restapi.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Minimal Spring Security configuration used exclusively in unit tests for
 * {@link ResourceController} and {@link HelloController}.
 *
 * <p>Note: tests that use this config must also set
 * {@code spring.config.location=classpath:/} (via {@code @TestPropertySource}
 * or in the YAML file) to prevent Spring Boot from loading the external
 * {@code config/application.yml} which contains a duplicate {@code management:}
 * key (a pre-existing defect from TASK-014).</p>
 *
 * <h2>Why this exists</h2>
 * The production {@code IdentitySecurityConfiguration} (from shared-identity)
 * requires {@code KEYCLOAK_ISSUER_URI} and {@code JWT_AUDIENCE} environment
 * variables at startup to configure the reactive JWT decoder via OIDC
 * discovery against a live Keycloak instance. Loading that configuration in
 * {@code @WebFluxTest} slice tests would force every developer to run Keycloak
 * locally — violating the "tests run without external infrastructure" principle.
 *
 * <p>This configuration replaces the production security chain with a
 * test-only chain that:
 * <ul>
 *   <li>Enables WebFlux security and method-level security
 *       ({@code @PreAuthorize} is honoured by the test slice).</li>
 *   <li>Uses the JWT resource server configured for
 *       {@link org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers#mockJwt()}
 *       — no JWKS endpoint or Keycloak required.</li>
 *   <li>Keeps the same path-level permit rules as production (all paths require
 *       authentication) so that the mock JWT authorities ({@code ROLE_SERVICE_USER},
 *       {@code ROLE_SERVICE_ADMIN}) drive the outcome of each test.</li>
 * </ul>
 *
 * <p>This class is annotated with {@code @TestConfiguration} so it is never
 * included in the production application context.
 */
@TestConfiguration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class TestSecurityConfig {

    /**
     * Security filter chain for tests.
     *
     * <p>All paths require authentication; method-level security
     * ({@code @PreAuthorize}) handles role granularity. Using
     * {@code jwt()} without a custom decoder instructs Spring Security
     * to accept mock JWTs injected via
     * {@code SecurityMockServerConfigurers.mockJwt()}.
     *
     * @param http the reactive HTTP security builder
     * @return the configured filter chain
     */
    @Bean
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {
                            // No custom decoder — Spring Security's test
                            // infrastructure (mockJwt mutator) bypasses
                            // decoder validation entirely.
                        })
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
