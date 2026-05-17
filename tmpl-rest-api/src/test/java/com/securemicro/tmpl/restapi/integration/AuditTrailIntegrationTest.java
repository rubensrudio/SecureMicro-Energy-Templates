package com.securemicro.tmpl.restapi.integration;

// CONTROL: INI-17

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.securemicro.shared.observability.AuditTrailService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration test for the audit trail feature.
 *
 * <h2>Purpose (TASK-018 / INI-17)</h2>
 * Verifies that {@code POST /api/v1/resources} with a valid {@code ROLE_SERVICE_ADMIN}
 * JWT triggers {@link AuditTrailService#audit(String, String, String, String, String)}
 * and that the resulting log event contains the required MDC fields:
 * {@code event-type=AUDIT}, {@code audit.subject}, {@code audit.action=CREATE},
 * {@code audit.result=SUCCESS}.
 *
 * <h2>Log capture strategy</h2>
 * <p>A Logback {@link ListAppender} is attached to the {@link AuditTrailService}
 * logger before the request is made.  After the request completes the appender's
 * list is inspected for an event whose MDC map contains {@code event-type=AUDIT}.
 * The appender is detached in the test body after assertions to avoid interference
 * with other tests.
 *
 * <h2>WireMock strategy</h2>
 * <p>Identical to {@link AuthenticationIntegrationTest}: a WireMock server simulates
 * Keycloak's OIDC discovery and JWKS endpoints so the real
 * {@code ReactiveJwtDecoder} validates tokens against the RSA key pair generated
 * in {@link #setUpWireMock()}.
 *
 * <p>CONTROL: INI-17
 */
// CONTROL: INI-17
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
class AuditTrailIntegrationTest {

    // ── Static fixtures shared across all tests in this class ─────────────────

    private static WireMockServer wireMockServer;
    private static KeyPair keyPair;
    private static final String KID = "audit-test-key-id";

    @Autowired
    private WebTestClient webTestClient;

    // ── Class-level setup / teardown ──────────────────────────────────────────

    /**
     * Generates an RSA-2048 key pair, starts WireMock on a dynamic port, and
     * registers OIDC discovery + JWKS stubs before the Spring context is refreshed.
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
     */
    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_ISSUER_URI", () -> "http://localhost:" + wireMockServer.port());
        registry.add("JWT_AUDIENCE", () -> "test");
    }

    // ── Test cases ─────────────────────────────────────────────────────────────

    /**
     * CONTROL: INI-17 — POST /api/v1/resources with ROLE_SERVICE_ADMIN must:
     * <ol>
     *   <li>Return HTTP 201 Created.</li>
     *   <li>Cause {@link AuditTrailService} to emit exactly one log event whose
     *       MDC map contains:
     *       <ul>
     *         <li>{@code event-type=AUDIT}</li>
     *         <li>{@code audit.subject} — non-null, non-empty JWT subject</li>
     *         <li>{@code audit.action=CREATE}</li>
     *         <li>{@code audit.result=SUCCESS}</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    // CONTROL: INI-17
    @Test
    @DisplayName("POST /api/v1/resources with ROLE_SERVICE_ADMIN -> 201 + AUDIT log emitted")
    void givenAdminToken_whenPostResources_thenHttp201AndAuditLogEmitted() throws Exception {
        // Arrange: attach a ListAppender to the AuditTrailService logger
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditTrailService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);

        try {
            String adminSubject = "admin-user-" + UUID.randomUUID();
            String token = createAdminJwt(adminSubject);

            // Act: POST /api/v1/resources with valid ADMIN token
            webTestClient.post()
                    .uri("/api/v1/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.status").isNotEmpty();

            // Assert: find the AUDIT log event in the captured list
            // CONTROL: INI-17
            ILoggingEvent auditEvent = listAppender.list.stream()
                    .filter(event -> "AUDIT".equals(event.getMDCPropertyMap().get("event-type")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No AUDIT log event was captured. "
                            + "Captured events: " + listAppender.list));

            Map<String, String> mdc = auditEvent.getMDCPropertyMap();

            // event-type must be "AUDIT"
            assertThat(mdc.get("event-type"))
                    .as("MDC field 'event-type' must equal 'AUDIT'")
                    .isEqualTo("AUDIT");

            // audit.subject must be present and match the JWT subject
            assertThat(mdc.get("audit.subject"))
                    .as("MDC field 'audit.subject' must be present and equal the JWT sub claim")
                    .isEqualTo(adminSubject);

            // audit.action must be CREATE
            assertThat(mdc.get("audit.action"))
                    .as("MDC field 'audit.action' must equal 'CREATE'")
                    .isEqualTo("CREATE");

            // audit.result must be SUCCESS
            assertThat(mdc.get("audit.result"))
                    .as("MDC field 'audit.result' must equal 'SUCCESS'")
                    .isEqualTo("SUCCESS");

        } finally {
            // Always detach appender to prevent log event leakage between tests
            auditLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    // ── JWT construction helpers ───────────────────────────────────────────────

    /**
     * Creates a signed, non-expired JWT with {@code ROLE_SERVICE_ADMIN} for use
     * in POST /api/v1/resources tests.
     *
     * @param subject the JWT {@code sub} claim value to embed
     * @return compact serialized JWT string
     */
    private String createAdminJwt(String subject) throws Exception {
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
                .claim("realm_access", Map.of("roles", List.of("ROLE_SERVICE_ADMIN")))
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
     * and {@code e} (public exponent) values.
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
