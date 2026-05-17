package com.securemicro.tmpl.restapi.security;

// CONTROL: RN-10
// CONTROL: INI-04

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityErrorHandler}.
 *
 * <p>Verifies the three criteria from TASK-017:
 * <ol>
 *   <li>(a) Expired / invalid JWT → HTTP 401, body does NOT contain
 *       {@code "exception"}, {@code "stackTrace"}, or {@code "at com."}
 *       (CONTROL: RN-10, INI-04).</li>
 *   <li>(b) IDP unreachable (network failure) → HTTP 503 with
 *       {@code {"error":"service_unavailable"}}, body is generic with no
 *       internal detail (CONTROL: RN-10).</li>
 *   <li>(c) No error body contains {@code "stackTrace"} or {@code "at com."}
 *       in any scenario (CONTROL: RN-10).</li>
 * </ol>
 *
 * <p>Tests use {@link MockServerWebExchange} and {@link MockServerHttpRequest}
 * from {@code spring-webflux} test support — no Spring context is loaded,
 * making these fast, hermetic unit tests.
 *
 * <p>CONTROL: RN-10 — stack traces never in response body.
 * <p>CONTROL: INI-04 — uniform error response format.
 */
// CONTROL: RN-10
// CONTROL: INI-04
class SecurityErrorHandlerTest {

    private SecurityErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SecurityErrorHandler();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates a {@link MockServerWebExchange} for a GET request to the given
     * URI. The mock response buffers are accessible via
     * {@link MockServerWebExchange#getResponse()}.
     */
    private MockServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(uri).build());
    }

    /**
     * Reads the response body from the mock exchange as a UTF-8 string.
     * Blocks until the reactive body is fully written.
     */
    private String responseBody(MockServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        // MockServerHttpResponse accumulates written buffers in getBodyAsString()
        return exchange.getResponse().getBodyAsString().block();
    }

    // ── (a) Expired / invalid JWT → 401, no "exception" field ───────────────

    /**
     * Criterion (a): A {@link JwtException} (e.g. expired token) must result
     * in HTTP 401 with body {@code {"error":"unauthorized"}}.
     *
     * <p>CONTROL: INI-04 — HTTP 401 for invalid tokens.
     * <p>CONTROL: RN-10 — no internal detail in body.
     */
    @Test
    @DisplayName("(a) JwtException (expired token) -> 401 with {error:unauthorized}")
    void givenExpiredJwt_whenHandle_thenHttp401WithGenericBody() {
        // CONTROL: INI-04
        MockServerWebExchange ex = exchange("/api/v1/resources");
        JwtException jwtEx = new JwtException("JWT expired at 2020-01-01T00:00:00Z");

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .as("Expired JWT must produce HTTP 401 (CONTROL: INI-04)")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String body = responseBody(ex);
        assertThat(body)
                .as("Body must contain error:unauthorized")
                .contains("\"error\"")
                .contains("unauthorized");

        // CONTROL: RN-10 — no exception field in body
        assertThat(body)
                .as("Body must NOT contain 'exception' field (CONTROL: RN-10)")
                .doesNotContain("exception");
        assertThat(body)
                .as("Body must NOT contain expiry detail (CONTROL: RN-10)")
                .doesNotContain("expired");
        assertThat(body)
                .as("Body must NOT contain JWT internal reason (CONTROL: RN-10)")
                .doesNotContain("2020-01-01");
    }

    /**
     * Criterion (a) — variant: {@link OAuth2AuthenticationException} for
     * invalid JWT (non-network error) must also produce HTTP 401.
     *
     * <p>CONTROL: INI-04, RN-10
     */
    @Test
    @DisplayName("(a) OAuth2AuthenticationException (invalid JWT) -> 401 with generic body")
    void givenOAuth2ExceptionForInvalidToken_whenHandle_thenHttp401() {
        // CONTROL: INI-04
        MockServerWebExchange ex = exchange("/api/v1/hello");
        OAuth2AuthenticationException oauthEx = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token", "The token has expired", null));

        StepVerifier.create(handler.handle(ex, oauthEx))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String body = responseBody(ex);
        assertThat(body).contains("\"error\"").contains("unauthorized");

        // CONTROL: RN-10 — the OAuth2 error description must NOT appear in the body
        assertThat(body)
                .as("OAuth2 error description must NOT appear in HTTP body (CONTROL: RN-10)")
                .doesNotContain("The token has expired");
        assertThat(body).doesNotContain("exception");
    }

    /**
     * Criterion (a): The 401 response body must NOT contain the field
     * {@code "exception"} in any form.
     *
     * <p>CONTROL: RN-10
     */
    @Test
    @DisplayName("(a) 401 body must not contain field 'exception'")
    void givenJwtException_whenHandle_thenBodyHasNoExceptionField() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/resources");
        JwtException jwtEx = new JwtException("internal validation detail");

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        String body = responseBody(ex);

        // CONTROL: RN-10 — no exception class, no stack trace fields
        assertThat(body).doesNotContain("exception");
        assertThat(body).doesNotContain("internal validation detail");
    }

    // ── (b) IDP unreachable → 503 with generic body ──────────────────────────

    /**
     * Criterion (b): When the root cause of a {@link JwtException} is a
     * {@link ConnectException} (Keycloak unreachable), the response must be
     * HTTP 503 with {@code {"error":"service_unavailable"}}.
     *
     * <p>CONTROL: RN-10 — no internal detail (host name, port, cause) in body.
     */
    @Test
    @DisplayName("(b) JwtException caused by ConnectException -> 503 with generic body")
    void givenJwtExceptionCausedByConnectException_whenHandle_thenHttp503() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/resources");

        ConnectException connectEx = new ConnectException(
                "Connection refused: keycloak.internal/10.0.0.1:8080");
        JwtException jwtEx = new JwtException(
                "An error occurred while attempting to decode the Jwt: "
                        + "Unable to resolve JWKS", connectEx);

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .as("IDP unreachable must produce HTTP 503")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        String body = responseBody(ex);
        assertThat(body).contains("\"error\"").contains("service_unavailable");

        // CONTROL: RN-10 — no internal detail: host, port, cause class in body
        assertThat(body)
                .as("Response body must NOT contain ConnectException detail (CONTROL: RN-10)")
                .doesNotContain("ConnectException");
        assertThat(body).doesNotContain("keycloak.internal");
        assertThat(body).doesNotContain("8080");
    }

    /**
     * Criterion (b): When the cause is {@link java.net.UnknownHostException}
     * (DNS failure for Keycloak JWKS endpoint), the response must be HTTP 503.
     *
     * <p>CONTROL: RN-10
     */
    @Test
    @DisplayName("(b) JwtException caused by UnknownHostException -> 503 generic body")
    void givenJwtExceptionCausedByUnknownHost_whenHandle_thenHttp503() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/hello");

        java.net.UnknownHostException dnsEx =
                new java.net.UnknownHostException("keycloak.internal: Name or service not known");
        JwtException jwtEx = new JwtException("Cannot resolve JWKS URI", dnsEx);

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        String body = responseBody(ex);
        assertThat(body).contains("service_unavailable");

        // CONTROL: RN-10 — no DNS detail in body
        assertThat(body).doesNotContain("keycloak.internal");
        assertThat(body).doesNotContain("UnknownHostException");
    }

    /**
     * Criterion (b): {@link OAuth2AuthenticationException} with a network
     * cause must also produce HTTP 503, not 401.
     *
     * <p>CONTROL: RN-10
     */
    @Test
    @DisplayName("(b) OAuth2AuthenticationException caused by network -> 503")
    void givenOAuth2ExceptionCausedByNetwork_whenHandle_thenHttp503() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/resources");

        ConnectException connectEx = new ConnectException("Connection timed out");
        OAuth2AuthenticationException oauthEx = new OAuth2AuthenticationException(
                new OAuth2Error("server_error"), connectEx);

        StepVerifier.create(handler.handle(ex, oauthEx))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        String body = responseBody(ex);
        assertThat(body).contains("service_unavailable");
        assertThat(body).doesNotContain("ConnectException");
        assertThat(body).doesNotContain("timed out");
    }

    // ── (c) No body contains "stackTrace" or "at com." ───────────────────────

    /**
     * Criterion (c): The 401 response body must NEVER contain the string
     * {@code "stackTrace"} or {@code "at com."}.
     *
     * <p>CONTROL: RN-10
     */
    @Test
    @DisplayName("(c) 401 body never contains 'stackTrace' or 'at com.'")
    void givenJwtException_whenHandle_thenBodyNeverContainsStackTraceMarkers() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/resources");
        JwtException jwtEx = new JwtException("token validation failed");

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        String body = responseBody(ex);

        // CONTROL: RN-10 — exact strings from the task acceptance criterion
        assertThat(body)
                .as("Body must NOT contain 'stackTrace' (CONTROL: RN-10)")
                .doesNotContain("stackTrace");
        assertThat(body)
                .as("Body must NOT contain 'at com.' (CONTROL: RN-10)")
                .doesNotContain("at com.");
    }

    /**
     * Criterion (c): The 503 response body must NEVER contain the string
     * {@code "stackTrace"} or {@code "at com."}.
     *
     * <p>CONTROL: RN-10
     */
    @Test
    @DisplayName("(c) 503 body never contains 'stackTrace' or 'at com.'")
    void givenIdpUnavailable_whenHandle_thenBodyNeverContainsStackTraceMarkers() {
        // CONTROL: RN-10
        MockServerWebExchange ex = exchange("/api/v1/resources");
        ConnectException connectEx = new ConnectException("refused");
        JwtException jwtEx = new JwtException("JWKS unavailable", connectEx);

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        String body = responseBody(ex);

        // CONTROL: RN-10 — exact strings from the task acceptance criterion
        assertThat(body)
                .as("Body must NOT contain 'stackTrace' (CONTROL: RN-10)")
                .doesNotContain("stackTrace");
        assertThat(body)
                .as("Body must NOT contain 'at com.' (CONTROL: RN-10)")
                .doesNotContain("at com.");
    }

    // ── Pass-through for unrelated exceptions ─────────────────────────────────

    /**
     * Exceptions that are not {@link JwtException} or
     * {@link OAuth2AuthenticationException} must be passed through to the
     * next handler in the chain (re-emitted as a {@link Mono#error}).
     *
     * <p>This ensures that {@link SecurityErrorHandler} does not swallow
     * unrelated application exceptions.
     */
    @Test
    @DisplayName("Non-JWT exception is passed through (not swallowed)")
    void givenUnrelatedRuntimeException_whenHandle_thenMonoErrorPassthrough() {
        MockServerWebExchange ex = exchange("/api/v1/resources");
        RuntimeException unrelated = new RuntimeException("some other error");

        StepVerifier.create(handler.handle(ex, unrelated))
                .expectError(RuntimeException.class)
                .verify();

        // Response must NOT have been written by this handler
        assertThat(ex.getResponse().getStatusCode())
                .as("Handler must not set status for unrelated exceptions")
                .isNull();
    }

    // ── Content-Type header ───────────────────────────────────────────────────

    /**
     * Verifies that 401 responses set Content-Type to application/json.
     */
    @Test
    @DisplayName("401 response sets Content-Type: application/json")
    void givenJwtException_whenHandle_thenContentTypeIsJson() {
        MockServerWebExchange ex = exchange("/api/v1/resources");
        JwtException jwtEx = new JwtException("expired");

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        assertThat(ex.getResponse().getHeaders().getContentType())
                .isNotNull();
        assertThat(ex.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }

    /**
     * Verifies that 503 responses set Content-Type to application/json.
     */
    @Test
    @DisplayName("503 response sets Content-Type: application/json")
    void givenIdpUnavailable_whenHandle_thenContentTypeIsJson() {
        MockServerWebExchange ex = exchange("/api/v1/resources");
        JwtException jwtEx = new JwtException("JWKS fetch failed",
                new ConnectException("refused"));

        StepVerifier.create(handler.handle(ex, jwtEx))
                .verifyComplete();

        assertThat(ex.getResponse().getHeaders().getContentType())
                .isNotNull();
        assertThat(ex.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }
}
