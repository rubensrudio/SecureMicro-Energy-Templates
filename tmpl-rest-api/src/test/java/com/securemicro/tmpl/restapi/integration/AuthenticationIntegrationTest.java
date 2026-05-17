package com.securemicro.tmpl.restapi.integration;

// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-07
// CONTROL: INI-08

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Full-context integration tests for authentication and authorization behaviour.
 *
 * <h2>Purpose (TASK-018 / INI-04, INI-05, INI-07, INI-08)</h2>
 * Exercises the real {@code IdentitySecurityConfiguration} and
 * {@code ReactiveJwtDecoder} against a WireMock server that simulates Keycloak's
 * OIDC discovery and JWKS endpoints.  No mocking of the JWT decoder — every JWT
 * is signed with a real RSA-2048 key pair and validated against the public key
 * exposed by WireMock.
 *
 * <h2>Scenarios covered</h2>
 * <ol>
 *   <li>{@code GET /api/v1/hello} without token → HTTP 401 (CONTROL: INI-04)</li>
 *   <li>{@code GET /api/v1/hello} with wrong role {@code ROLE_OTHER} → HTTP 403
 *       (CONTROL: INI-05)</li>
 *   <li>{@code GET /api/v1/hello} with {@code ROLE_SERVICE_USER} → HTTP 200
 *       (CONTROL: INI-04, INI-05)</li>
 *   <li>{@code GET /actuator/health} without token → HTTP 200 (CONTROL: INI-08)</li>
 *   <li>{@code GET /api/v1/hello} with expired token → HTTP 401 (CONTROL: INI-07)</li>
 * </ol>
 *
 * <h2>WireMock strategy</h2>
 * <p>A {@link WireMockServer} is started on a dynamic port in {@link #setUpWireMock()}.
 * Two stubs are registered:
 * <ul>
 *   <li>{@code GET /.well-known/openid-configuration} — OIDC discovery document
 *       pointing to the WireMock server for the JWKS endpoint.</li>
 *   <li>{@code GET /protocol/openid-connect/certs} — JWKS document with the RSA
 *       public key generated in {@link #setUpWireMock()}.</li>
 * </ul>
 *
 * <h2>JWT creation</h2>
 * <p>JWTs are created using {@code com.nimbusds.jwt.SignedJWT} (available via
 * {@code nimbus-jose-jwt}, which is pulled transitively by
 * {@code spring-security-oauth2-jose}).  Claims include issuer, audience, subject,
 * expiry, and Keycloak-style {@code realm_access.roles}.
 *
 * <p>CONTROL: INI-04 INI-05 INI-07 INI-08
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-07
// CONTROL: INI-08
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.vault.enabled=false",
        "spring.config.import=",
        "keycloak.client-secret=test-placeholder",
        "keycloak.jwt-audience=test",
        "service.api-key=test-placeholder",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.prometheus.enabled=true",
        "management.health.livenessstate.enabled=true",
        "management.health.readinessstate.enabled=true",
        // Spring Boot 3.x Prometheus export property (different namespace from 2.x)
        "management.prometheus.metrics.export.enabled=true",
        "server.error.include-stacktrace=never",
        "server.error.include-exception=false",
        "server.error.include-message=never"
        // KEYCLOAK_ISSUER_URI and JWT_AUDIENCE injected via @DynamicPropertySource
})
class AuthenticationIntegrationTest {

    // ── Static fixtures shared across all tests in this class ─────────────────

    private static WireMockServer wireMockServer;
    private static KeyPair keyPair;
    private static final String KID = "test-key-id";

    @Autowired
    private WebTestClient webTestClient;

    // ── Class-level setup / teardown ──────────────────────────────────────────

    /**
     * Generates an RSA-2048 key pair, starts WireMock on a dynamic port, and
     * registers OIDC discovery + JWKS stubs before the Spring context is refreshed.
     *
     * <p>The WireMock stubs must be in place before the context starts because
     * {@link com.securemicro.shared.identity.IdentitySecurityConfiguration#jwtDecoder()}
     * calls {@code ReactiveJwtDecoders.fromIssuerLocation(issuerUri)} during context
     * refresh, which performs an HTTP GET against the OIDC discovery URL.
     */
    @BeforeAll
    static void setUpWireMock() throws Exception {
        // Generate RSA-2048 key pair for JWT signing / JWKS
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        // Start WireMock on a random port
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        String baseUrl = "http://localhost:" + wireMockServer.port();

        // Stub: OIDC discovery document
        wireMockServer.stubFor(
                get(urlEqualTo("/.well-known/openid-configuration"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(buildOidcDiscoveryDocument(baseUrl)))
        );

        // Stub: JWKS endpoint with the RSA public key
        wireMockServer.stubFor(
                get(urlEqualTo("/protocol/openid-connect/certs"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(buildJwksDocument((RSAPublicKey) keyPair.getPublic())))
        );
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Injects the WireMock server URL as {@code KEYCLOAK_ISSUER_URI} and
     * {@code JWT_AUDIENCE} into the Spring context before it is refreshed.
     *
     * <p>This must run AFTER {@link #setUpWireMock()} (WireMock must be running)
     * and BEFORE the context starts (so the decoder resolves the correct URL).
     * Spring guarantees {@code @DynamicPropertySource} executes after all
     * {@code @BeforeAll} methods and before context refresh.
     */
    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_ISSUER_URI", () -> "http://localhost:" + wireMockServer.port());
        registry.add("JWT_AUDIENCE", () -> "test");
    }

    // ── Test cases ─────────────────────────────────────────────────────────────

    /**
     * CONTROL: INI-04 — request without Bearer token must return HTTP 401.
     */
    // CONTROL: INI-04
    @Test
    @DisplayName("GET /api/v1/hello without token -> 401")
    void givenNoToken_whenGetHello_thenHttp401() {
        webTestClient.get()
                .uri("/api/v1/hello")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized");
    }

    /**
     * CONTROL: INI-05 — valid JWT with wrong role must return HTTP 403.
     */
    // CONTROL: INI-05
    @Test
    @DisplayName("GET /api/v1/hello with ROLE_OTHER -> 403")
    void givenWrongRole_whenGetHello_thenHttp403() throws Exception {
        String token = createJwt("test-user", "ROLE_OTHER");

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("forbidden");
    }

    /**
     * CONTROL: INI-04, INI-05 — valid JWT with ROLE_SERVICE_USER must return HTTP 200.
     */
    // CONTROL: INI-04
    // CONTROL: INI-05
    @Test
    @DisplayName("GET /api/v1/hello with ROLE_SERVICE_USER -> 200")
    void givenServiceUserRole_whenGetHello_thenHttp200() throws Exception {
        String token = createJwt("test-user", "ROLE_SERVICE_USER");

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * CONTROL: INI-08 — health endpoint must return HTTP 200 without authentication.
     */
    // CONTROL: INI-08
    @Test
    @DisplayName("GET /actuator/health without token -> 200")
    void givenNoToken_whenGetHealth_thenHttp200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * CONTROL: INI-07 — expired JWT must be rejected with HTTP 401.
     */
    // CONTROL: INI-07
    @Test
    @DisplayName("GET /api/v1/hello with expired token -> 401")
    void givenExpiredToken_whenGetHello_thenHttp401() throws Exception {
        String token = createExpiredJwt("test-user", "ROLE_SERVICE_USER");

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── JWT construction helpers ───────────────────────────────────────────────

    /**
     * Creates a signed, non-expired JWT with the given subject and roles.
     *
     * <p>Claims mirror Keycloak's token format:
     * <ul>
     *   <li>{@code iss} — WireMock base URL (matches {@code KEYCLOAK_ISSUER_URI})</li>
     *   <li>{@code aud} — {@code ["test"]} (matches {@code JWT_AUDIENCE})</li>
     *   <li>{@code sub} — {@code subject} parameter</li>
     *   <li>{@code exp} — now + 5 minutes</li>
     *   <li>{@code iat} — now</li>
     *   <li>{@code realm_access.roles} — {@code roles} vararg</li>
     * </ul>
     *
     * @param subject the JWT {@code sub} claim
     * @param roles   one or more Keycloak realm roles
     * @return a compact serialized JWT string
     */
    private String createJwt(String subject, String... roles) throws Exception {
        String issuer = "http://localhost:" + wireMockServer.port();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 5 * 60 * 1000); // +5 minutes

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience("test")
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(now)
                .expirationTime(expiry)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID)
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    /**
     * Creates a signed, already-expired JWT with the given subject and roles.
     *
     * <p>The {@code exp} claim is set to now &minus; 5 minutes, ensuring the
     * token is rejected by {@code NimbusReactiveJwtDecoder}'s timestamp validator.
     *
     * @param subject the JWT {@code sub} claim
     * @param roles   one or more Keycloak realm roles
     * @return a compact serialized (but expired) JWT string
     */
    private String createExpiredJwt(String subject, String... roles) throws Exception {
        String issuer = "http://localhost:" + wireMockServer.port();
        Date now = new Date();
        Date past = new Date(now.getTime() - 5 * 60 * 1000); // -5 minutes (expired)

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience("test")
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(past)
                .expirationTime(past) // already expired
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID)
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    // ── WireMock stub body builders ────────────────────────────────────────────

    /**
     * Builds a minimal OIDC discovery document JSON string pointing to the
     * WireMock server for the JWKS endpoint.
     *
     * @param baseUrl WireMock base URL ({@code http://localhost:<port>})
     * @return JSON string
     */
    private static String buildOidcDiscoveryDocument(String baseUrl) {
        return "{"
                + "\"issuer\":\"" + baseUrl + "\","
                + "\"jwks_uri\":\"" + baseUrl + "/protocol/openid-connect/certs\","
                + "\"authorization_endpoint\":\"" + baseUrl + "/protocol/openid-connect/auth\","
                + "\"token_endpoint\":\"" + baseUrl + "/protocol/openid-connect/token\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
                + "}";
    }

    /**
     * Builds a JWKS document containing the given RSA public key.
     *
     * <p>The key entry includes {@code "use":"sig"}, {@code "kty":"RSA"},
     * {@code "alg":"RS256"}, and the Base64url-encoded {@code n} (modulus)
     * and {@code e} (public exponent) values required by the
     * {@link com.nimbusds.jose} JWKS parser.
     *
     * @param publicKey the RSA public key to publish
     * @return JWKS JSON string
     */
    private static String buildJwksDocument(RSAPublicKey publicKey) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(publicKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(publicKey.getPublicExponent().toByteArray());

        return "{"
                + "\"keys\":["
                + "{"
                + "\"kty\":\"RSA\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"kid\":\"" + KID + "\","
                + "\"n\":\"" + n + "\","
                + "\"e\":\"" + e + "\""
                + "}"
                + "]"
                + "}";
    }
}
