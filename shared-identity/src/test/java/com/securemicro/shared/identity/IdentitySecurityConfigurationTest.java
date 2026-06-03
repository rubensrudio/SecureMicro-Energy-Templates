package com.securemicro.shared.identity;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IdentitySecurityConfiguration}.
 *
 * <p>Uses WireMock to simulate Keycloak's OIDC discovery endpoint
 * ({@code /.well-known/openid-configuration}) and JWKS endpoint
 * ({@code /protocol/openid-connect/certs}).  JWTs are signed with an
 * RSA key generated in-process; the matching public key is served via
 * the mocked JWKS endpoint so that the {@link
 * org.springframework.security.oauth2.jwt.ReactiveJwtDecoder} can
 * verify signatures without a live Keycloak instance.
 *
 * <p>Scenarios verified:
 * <ol>
 *   <li>Request without token → HTTP 401, body {@code {"error":"unauthorized"}} (INI-04)</li>
 *   <li>Valid token, correct role → request proceeds, HTTP 200 (INI-05)</li>
 *   <li>Valid token, wrong role → HTTP 403, body {@code {"error":"forbidden"}} (INI-05)</li>
 *   <li>{@code KEYCLOAK_ISSUER_URI} read from environment property, not hardcoded (INI-06)</li>
 *   <li>Expired token → HTTP 401 (INI-07 — exp validation)</li>
 *   <li>Actuator health path accessible without token (INI-08)</li>
 * </ol>
 *
 * <p>CONTROL: INI-04 INI-05 INI-06 INI-07 INI-08
 */
// CONTROL: INI-04
// CONTROL: INI-05
// CONTROL: INI-06
// CONTROL: INI-07
// CONTROL: INI-08
@SpringBootTest(
        classes = TestSecurityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // CONTROL: INI-06 — issuer URI is supplied via property (env var simulation);
                // the value is intentionally set to the WireMock base URL so the decoder
                // can perform OIDC discovery against the mock server.  The property name
                // KEYCLOAK_ISSUER_URI is what the production configuration binds to — this
                // test proves the binding works without any hardcoded literal in production code.
                "KEYCLOAK_ISSUER_URI=http://localhost:${test.wiremock.port}/realms/energy"
        }
)
@AutoConfigureWebTestClient
class IdentitySecurityConfigurationTest {

    // ── WireMock lifecycle ──────────────────────────────────────────────────

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static String issuerUrl;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() throws Exception {
        // Generate a test RSA key pair — private key signs JWTs, public key
        // is served via the mocked JWKS endpoint for signature verification.
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate();

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        // The issuer URL must match the iss claim in every JWT we mint.
        issuerUrl = "http://localhost:" + wireMock.port() + "/realms/energy";

        // Tell the Spring context which port WireMock is listening on so
        // the ${test.wiremock.port} placeholder resolves correctly.
        System.setProperty("test.wiremock.port", String.valueOf(wireMock.port()));

        stubOidcDiscovery();
        stubJwks();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
        System.clearProperty("test.wiremock.port");
    }

    @BeforeEach
    void resetWireMockRequests() {
        wireMock.resetRequests();
    }

    // ── WireMock stubs ──────────────────────────────────────────────────────

    /**
     * Stubs the OpenID Connect discovery document so that
     * {@link org.springframework.security.oauth2.jwt.ReactiveJwtDecoders#fromIssuerLocation}
     * can resolve the JWKS URI without a live Keycloak server.
     */
    private static void stubOidcDiscovery() {
        String discoveryBody = """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s/protocol/openid-connect/certs",
                  "token_endpoint": "%s/protocol/openid-connect/token",
                  "authorization_endpoint": "%s/protocol/openid-connect/auth",
                  "subject_types_supported": ["public"],
                  "id_token_signing_alg_values_supported": ["RS256"],
                  "response_types_supported": ["code"]
                }
                """.formatted(issuerUrl, issuerUrl, issuerUrl, issuerUrl);

        wireMock.stubFor(get(urlEqualTo("/realms/energy/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(discoveryBody)));
    }

    /**
     * Stubs the JWKS endpoint so the decoder can retrieve the RSA public key
     * used to verify JWT signatures.
     */
    private static void stubJwks() throws Exception {
        // Expose only the public part of the RSA key in the JWKS document.
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        String jwksBody = jwkSet.toString(); // JSON representation

        wireMock.stubFor(get(urlEqualTo("/realms/energy/protocol/openid-connect/certs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksBody)));
    }

    // ── JWT factory helpers ─────────────────────────────────────────────────

    /**
     * Mints a signed JWT with the specified roles and a 1-hour expiry.
     *
     * @param roles Keycloak realm roles to embed in {@code realm_access.roles}.
     * @return compact serialised JWT string.
     */
    private String mintValidToken(List<String> roles) throws Exception {
        return mintToken(roles, Instant.now().plusSeconds(3600));
    }

    /**
     * Mints a signed JWT that is already expired.
     */
    private String mintExpiredToken() throws Exception {
        return mintToken(List.of("ROLE_SERVICE_USER"), Instant.now().minusSeconds(60));
    }

    private String mintToken(List<String> roles, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user-" + UUID.randomUUID())
                .issuer(issuerUrl)
                .audience("tmpl-rest-api")
                .expirationTime(Date.from(expiry))
                .issueTime(Date.from(Instant.now()))
                .claim("realm_access", Map.of("roles", roles))
                .build();

        JWSSigner signer = new RSASSASigner(rsaKey);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaKey.getKeyID())
                        .build(),
                claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    // ── Test cases ──────────────────────────────────────────────────────────

    /**
     * Criterion (a): Request without Authorization header must return
     * HTTP 401 with body {@code {"error":"unauthorized"}}.
     *
     * <p>CONTROL: INI-04
     */
    @Test
    @DisplayName("(a) Request without token -> 401 with body {error:unauthorized}")
    void givenNoToken_whenRequestSecuredEndpoint_thenHttp401WithGenericBody() {
        // CONTROL: INI-04
        webTestClient.get()
                .uri("/api/v1/hello")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                // Verify no internal detail leaks (CONTROL: RN-10)
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .jsonPath("$.trace").doesNotExist();
    }

    /**
     * Criterion (b): Valid token with the correct role must allow the
     * request to proceed (HTTP 200 from the downstream handler).
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("(b) Valid token with correct role -> request proceeds (200)")
    void givenValidTokenWithCorrectRole_whenRequestSecuredEndpoint_thenHttp200() throws Exception {
        // CONTROL: INI-05
        String token = mintValidToken(List.of("ROLE_SERVICE_USER"));

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Criterion (c): Valid token with an insufficient role must return
     * HTTP 403 with body {@code {"error":"forbidden"}}.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("(c) Valid token with wrong role -> 403 with body {error:forbidden}")
    void givenValidTokenWithWrongRole_whenRequestAdminEndpoint_thenHttp403WithGenericBody()
            throws Exception {
        // CONTROL: INI-05
        // Mint a token that only carries ROLE_SERVICE_USER but the endpoint
        // requires ROLE_SERVICE_ADMIN.
        String token = mintValidToken(List.of("ROLE_SERVICE_USER"));

        webTestClient.post()
                .uri("/api/v1/admin")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.error").isEqualTo("forbidden")
                // Verify no internal detail leaks (CONTROL: RN-10)
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .jsonPath("$.trace").doesNotExist();
    }

    /**
     * Criterion (d): KEYCLOAK_ISSUER_URI is read from the environment
     * property, never from a hardcoded literal.
     *
     * <p>This test verifies the binding by asserting that:
     * <ol>
     *   <li>The Spring context started successfully with the property
     *       injected (if the @Value binding were hardcoded, the context
     *       would fail to start with the WireMock URL).</li>
     *   <li>The decoder validates a token signed against the WireMock
     *       issuer — proving the decoder was configured from the property.</li>
     * </ol>
     *
     * <p>CONTROL: INI-06
     */
    @Test
    @DisplayName("(d) KEYCLOAK_ISSUER_URI read from env property, not hardcoded")
    void givenIssuerUriFromProperty_whenValidToken_thenContextUsesPropertyValue()
            throws Exception {
        // CONTROL: INI-06
        // The issuer in the JWT matches the WireMock URL injected via the
        // KEYCLOAK_ISSUER_URI property.  If the code hardcoded a different
        // URL, the decoder would reject this token.
        String token = mintValidToken(List.of("ROLE_SERVICE_USER"));

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                // Token validates -> context correctly used the env property value
                .expectStatus().isOk();

        // Additionally verify the IdentitySecurityConfiguration class source
        // does NOT contain any hardcoded issuer URL literal by inspecting the
        // class fields at runtime through reflection — only @Value is allowed.
        var fields = IdentitySecurityConfiguration.class.getDeclaredFields();
        for (var field : fields) {
            if (field.getName().equals("issuerUri")) {
                assertThat(field.isAnnotationPresent(org.springframework.beans.factory.annotation.Value.class))
                        .as("issuerUri field must carry @Value — hardcoded literals are forbidden (CONTROL: INI-06)")
                        .isTrue();
                var valueAnnotation = field.getAnnotation(org.springframework.beans.factory.annotation.Value.class);
                assertThat(valueAnnotation.value())
                        .as("@Value expression must reference KEYCLOAK_ISSUER_URI property")
                        .contains("KEYCLOAK_ISSUER_URI");
            }
        }
    }

    /**
     * Expired token must return HTTP 401 (INI-07 — exp claim validation).
     *
     * <p>CONTROL: INI-07
     */
    @Test
    @DisplayName("Expired token -> 401 with generic body (exp validation)")
    void givenExpiredToken_whenRequestSecuredEndpoint_thenHttp401() throws Exception {
        // CONTROL: INI-07
        String token = mintExpiredToken();

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.exception").doesNotExist();
    }

    /**
     * Actuator health endpoint must be accessible without a token (INI-08).
     *
     * <p>CONTROL: INI-08
     */
    @Test
    @DisplayName("Actuator health -> 200 without token (public endpoint)")
    void givenNoToken_whenRequestActuatorHealth_thenHttp200() {
        // CONTROL: INI-08
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Actuator health sub-paths must also be accessible without a token (INI-08).
     *
     * <p>CONTROL: INI-08
     */
    @Test
    @DisplayName("Actuator health/** -> 200 without token (wildcard match)")
    void givenNoToken_whenRequestActuatorHealthSubpath_thenHttp200() {
        // CONTROL: INI-08
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Actuator prometheus endpoint must be accessible without a token (INI-08).
     *
     * <p>CONTROL: INI-08
     */
    @Test
    @DisplayName("Actuator prometheus -> 200 without token (public endpoint)")
    void givenNoToken_whenRequestActuatorPrometheus_thenHttp200() {
        // CONTROL: INI-08
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                // The test application exposes a stub for this path — we only
                // care that it is NOT rejected by security (not 401/403).
                .expectStatus().isOk();
    }

    /**
     * Token Confusion Attack prevention: a JWT with a valid signature and issuer
     * but with {@code aud: wrong-audience} must be rejected with HTTP 401.
     *
     * <p>Verifies CONTROL: INI-07 — audience ({@code aud}) validation.
     * Without the {@link JwtClaimValidator} registered in {@link
     * IdentitySecurityConfiguration#jwtDecoder()}, a token issued to a
     * different client of the same Keycloak realm would be accepted (CWE-284).
     *
     * <p>Also asserts that no internal exception detail leaks in the response
     * body (CONTROL: RN-10).
     *
     * <p>CONTROL: INI-07 RN-10
     */
    @Test
    @DisplayName("Token with wrong audience -> 401 (Token Confusion Attack prevention, INI-07)")
    void givenTokenWithWrongAudience_whenRequestSecuredEndpoint_thenHttp401WithGenericBody()
            throws Exception {
        // CONTROL: INI-07
        // Mint a JWT that is signed with the correct RSA key and carries the
        // correct issuer, but targets a different client audience.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user-wrong-aud")
                .issuer(issuerUrl)
                .audience("wrong-audience")   // <-- different client in same realm
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .issueTime(Date.from(Instant.now()))
                .claim("realm_access", Map.of("roles", List.of("ROLE_SERVICE_USER")))
                .build();

        JWSSigner signer = new RSASSASigner(rsaKey);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaKey.getKeyID())
                        .build(),
                claims);
        signedJWT.sign(signer);
        String token = signedJWT.serialize();

        webTestClient.get()
                .uri("/api/v1/hello")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                // CONTROL: RN-10 — no internal detail in the response
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .jsonPath("$.trace").doesNotExist();
    }
}
