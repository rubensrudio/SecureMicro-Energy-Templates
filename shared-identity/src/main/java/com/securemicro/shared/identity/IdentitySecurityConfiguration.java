package com.securemicro.shared.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Spring Security WebFlux resource-server configuration for the
 * SecureMicro Energy Templates platform.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Configures JWT validation via Keycloak's JWKS endpoint (CONTROL: INI-07).</li>
 *   <li>Reads the Keycloak issuer URI exclusively from the {@code KEYCLOAK_ISSUER_URI}
 *       environment variable / system property — never from a hardcoded literal
 *       (CONTROL: INI-06).</li>
 *   <li>Protects all paths except {@code /actuator/health/**} and
 *       {@code /actuator/prometheus} (CONTROL: INI-08).</li>
 *   <li>Returns HTTP 401 with a generic body for absent or invalid tokens
 *       (CONTROL: INI-04 / RN-10).</li>
 *   <li>Returns HTTP 403 with a generic body for insufficient roles
 *       (CONTROL: INI-05 / RN-10).</li>
 * </ul>
 *
 * <p>Security properties guaranteed by this class:
 * <ul>
 *   <li>Stack traces are NEVER exposed in error responses (CONTROL: RN-10).</li>
 *   <li>Error bodies are generic — they reveal no information about the
 *       reason for rejection (CONTROL: INI-04, INI-05).</li>
 *   <li>CSRF is disabled — this is a stateless REST API using Bearer tokens.</li>
 * </ul>
 *
 * <p>CONTROL: INI-04 INI-05 INI-06 INI-07 RN-02
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-06
// CONTROL: INI-07
// CONTROL: RN-02
@Configuration
@EnableWebFluxSecurity
public class IdentitySecurityConfiguration {

    /**
     * Keycloak issuer URI — injected exclusively from the
     * {@code KEYCLOAK_ISSUER_URI} environment variable.
     *
     * <p>CONTROL: INI-06 — never hardcoded; always resolved from environment.
     */
    // CONTROL: INI-06
    @Value("${KEYCLOAK_ISSUER_URI}")
    private String issuerUri;

    /** Generic error body written for HTTP 401 responses. */
    private static final byte[] BODY_UNAUTHORIZED =
            "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);

    /** Generic error body written for HTTP 403 responses. */
    private static final byte[] BODY_FORBIDDEN =
            "{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8);

    /**
     * Configures the reactive security filter chain.
     *
     * <ul>
     *   <li>Actuator health and prometheus paths are open (no authentication).</li>
     *   <li>All other paths require a valid JWT bearer token.</li>
     *   <li>Authentication errors return 401 with {@code {"error":"unauthorized"}}.</li>
     *   <li>Authorization errors return 403 with {@code {"error":"forbidden"}}.</li>
     *   <li>CSRF disabled — stateless REST API.</li>
     * </ul>
     *
     * <p>CONTROL: INI-04 INI-05 INI-07 INI-08 RN-02 RN-10
     */
    // CONTROL: INI-04
    // CONTROL: INI-05
    // CONTROL: INI-07
    // CONTROL: INI-08
    // CONTROL: RN-02
    // CONTROL: RN-10
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        // Health and metrics endpoints open without authentication.
                        // CONTROL: INI-08
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus")
                                .permitAll()
                        // Every other exchange requires a valid JWT.
                        // CONTROL: INI-04 | INI-07
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                // Extract roles from Keycloak's realm_access.roles claim.
                                // CONTROL: INI-05
                                .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                        )
                        // 401 handler — returns generic body, no internal detail.
                        // CONTROL: INI-04 | RN-10
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders()
                                    .setContentType(MediaType.APPLICATION_JSON);
                            DataBuffer buffer = exchange.getResponse()
                                    .bufferFactory()
                                    .wrap(BODY_UNAUTHORIZED);
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        })
                        // 403 handler — returns generic body, no internal detail.
                        // CONTROL: INI-05 | RN-10
                        .accessDeniedHandler((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            exchange.getResponse().getHeaders()
                                    .setContentType(MediaType.APPLICATION_JSON);
                            DataBuffer buffer = exchange.getResponse()
                                    .bufferFactory()
                                    .wrap(BODY_FORBIDDEN);
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        })
                )
                // Disable CSRF: stateless REST API — tokens carried in Authorization header.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    /**
     * Reactive JWT decoder that validates tokens issued by Keycloak.
     *
     * <p>Uses OIDC Discovery ({@code /.well-known/openid-configuration}) to
     * resolve the JWKS URI automatically, enabling:
     * <ul>
     *   <li>Signature validation via the public RSA keys advertised in the JWKS
     *       endpoint (CONTROL: INI-07).</li>
     *   <li>Expiration ({@code exp}) and not-before ({@code nbf}) checks
     *       (CONTROL: INI-07).</li>
     *   <li>Issuer ({@code iss}) validation against {@code KEYCLOAK_ISSUER_URI}
     *       (CONTROL: INI-06, INI-07).</li>
     * </ul>
     *
     * <p>Audience ({@code aud}) validation is expected to be enforced by the
     * consuming application via a custom validator registered on this decoder
     * or via Spring Security's default audience validator when the
     * {@code spring.security.oauth2.resourceserver.jwt.audiences} property is set.
     *
     * <p>CONTROL: INI-06 INI-07
     *
     * @return a {@link ReactiveJwtDecoder} backed by Keycloak's JWKS endpoint.
     */
    // CONTROL: INI-06
    // CONTROL: INI-07
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // ReactiveJwtDecoders.fromIssuerLocation performs OIDC discovery
        // against KEYCLOAK_ISSUER_URI and fetches the JWKS URI from the
        // openid-configuration document. No hardcoded URLs.
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * Converter that extracts Keycloak roles from the {@code realm_access.roles}
     * claim and maps them to Spring Security {@link GrantedAuthority} instances.
     *
     * <p>Keycloak does not place roles in the standard {@code scope} or {@code scp}
     * claim; instead they live in a nested {@code realm_access.roles} array.  Without
     * this converter, Spring Security would find no authorities in the token and every
     * {@code @PreAuthorize} expression would evaluate to {@code false}.
     *
     * <p>CONTROL: INI-05 — role-based authorization requires roles to be extracted.
     *
     * @return a reactive converter adapter wrapping the Keycloak-aware converter.
     */
    // CONTROL: INI-05
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(keycloakGrantedAuthoritiesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    /**
     * Extracts {@code realm_access.roles} from a Keycloak JWT and converts each
     * role string to a {@link SimpleGrantedAuthority}.
     *
     * <p>Roles are preserved as-is (e.g. {@code ROLE_SERVICE_USER}) — no prefix
     * is added or stripped here; callers must use the exact role name in
     * {@code @PreAuthorize("hasAuthority('ROLE_SERVICE_USER')")} expressions.
     *
     * <p>CONTROL: INI-05
     */
    // CONTROL: INI-05
    private Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter() {
        return jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) {
                return List.of();
            }
            Object rolesObj = realmAccess.get("roles");
            if (!(rolesObj instanceof List<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
        };
    }
}
