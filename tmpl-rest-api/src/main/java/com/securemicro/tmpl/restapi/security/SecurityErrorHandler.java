package com.securemicro.tmpl.restapi.security;

// CONTROL: RN-10
// CONTROL: INI-04

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Reactive {@link WebExceptionHandler} that intercepts security-related
 * exceptions thrown during JWT decoding and translates them into safe,
 * generic HTTP error responses — never exposing stack traces or internal
 * detail to the caller.
 *
 * <h2>Covered edge cases (per spec section "Edge Cases")</h2>
 * <ol>
 *   <li><b>Token expired / invalid JWT</b> — {@link JwtException} and
 *       {@link OAuth2AuthenticationException} where the root cause is NOT a
 *       network failure: returns HTTP 401 with {@code {"error":"unauthorized"}}.
 *       Internal log records the full exception for post-incident analysis.
 *       (CONTROL: INI-04, RN-10)</li>
 *   <li><b>Keycloak / IDP unreachable</b> — {@link JwtException} or
 *       {@link OAuth2AuthenticationException} where the root cause IS a
 *       network failure ({@link ConnectException}, {@link UnknownHostException},
 *       {@link java.net.SocketTimeoutException}, or any {@code IOException}
 *       in the causal chain): returns HTTP 503 with
 *       {@code {"error":"service_unavailable"}}. Internal log records the full
 *       exception including cause for operations visibility.
 *       (CONTROL: INI-04, RN-10)</li>
 * </ol>
 *
 * <h2>What this handler does NOT replace</h2>
 * <p>The {@code SecurityWebFilterChain} in
 * {@code IdentitySecurityConfiguration} (shared-identity, TASK-009) already
 * handles the most common 401 / 403 cases via its {@code authenticationEntryPoint}
 * and {@code accessDeniedHandler}. This handler complements those by catching
 * {@link JwtException} instances that escape the OAuth2 filter layer — e.g.
 * when the JWKS endpoint is unreachable and the decoder throws instead of
 * returning an error signal handled by the entry point.
 *
 * <h2>Ordering</h2>
 * <p>Registered at {@link Order} {@code -2} — after Spring Security's
 * internal handlers (order {@code -1}) but before the default Spring Boot
 * error handler ({@code DefaultErrorWebExceptionHandler}, order {@code -1}
 * as well; ours wins due to lower numerical value in Spring's convention
 * where more negative = higher precedence). This guarantees our handler
 * intercepts JWT errors before they reach the default Spring Boot error page
 * which could expose stack traces (CONTROL: RN-10).
 *
 * <h2>Stack trace guarantee (CONTROL: RN-10)</h2>
 * <p>No stack trace, exception class name, or internal message is ever
 * written to the HTTP response body. All exception detail is written
 * exclusively to the server-side log for operational visibility.
 *
 * @see org.springframework.security.oauth2.jwt.JwtException
 * @see org.springframework.security.oauth2.core.OAuth2AuthenticationException
 */
// CONTROL: RN-10
// CONTROL: INI-04
@Component
@Order(-2)
public class SecurityErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandler.class);

    /** Generic 401 response body — no internal detail. CONTROL: RN-10 */
    // CONTROL: RN-10
    private static final byte[] BODY_UNAUTHORIZED =
            "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);

    /** Generic 503 response body — no internal detail. CONTROL: RN-10 */
    // CONTROL: RN-10
    private static final byte[] BODY_SERVICE_UNAVAILABLE =
            "{\"error\":\"service_unavailable\"}".getBytes(StandardCharsets.UTF_8);

    // -------------------------------------------------------------------------

    /**
     * Intercepts {@link JwtException} and {@link OAuth2AuthenticationException}
     * thrown during JWT decoding and routes them to either a 401 or 503
     * response depending on whether the root cause is a network failure.
     *
     * <p>All other exceptions are passed through to the next handler in the
     * chain so that the {@code DefaultErrorWebExceptionHandler} (or any other
     * registered handler) can process them.
     *
     * @param exchange the current server exchange
     * @param ex       the exception thrown during request processing
     * @return a {@link Mono} that completes when the response has been written,
     *         or that re-throws {@code ex} if this handler does not apply
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // CONTROL: INI-04 — JWT-related exceptions are the only ones handled here.
        if (ex instanceof JwtException || ex instanceof OAuth2AuthenticationException) {
            if (isIdpUnavailable(ex)) {
                return handleIdpUnavailable(exchange, ex);
            }
            return handleUnauthorized(exchange, ex);
        }
        // Not our exception — pass through to the next handler.
        return Mono.error(ex);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Determines whether the exception (or any cause in its causal chain)
     * signals that the IDP / Keycloak JWKS endpoint is unreachable.
     *
     * <p>When {@link org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder}
     * fails to fetch the JWKS document (e.g. Keycloak is down), it wraps the
     * underlying network exception ({@link ConnectException},
     * {@link UnknownHostException}, {@link java.io.IOException}) inside a
     * {@link JwtException} or {@link OAuth2AuthenticationException}.
     *
     * @param ex the thrown exception
     * @return {@code true} if any cause in the chain is a network-related I/O
     *         exception
     */
    private boolean isIdpUnavailable(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.io.IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Writes an HTTP 401 response with {@code {"error":"unauthorized"}}.
     *
     * <p>Full exception detail (message + stack trace) is written to the
     * server-side log for post-incident analysis — NEVER to the response body.
     * CONTROL: RN-10, INI-04
     *
     * @param exchange the current server exchange
     * @param ex       the JWT exception triggering this response
     * @return a {@link Mono} completing when the response body has been written
     */
    // CONTROL: RN-10
    // CONTROL: INI-04
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, Throwable ex) {
        // CONTROL: RN-10 — full detail logged server-side only, never in response
        log.warn("JWT validation failed (unauthorized): {}", ex.getMessage(), ex);

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(BODY_UNAUTHORIZED);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Writes an HTTP 503 response with {@code {"error":"service_unavailable"}}.
     *
     * <p>Full exception detail (message + stack trace) is written to the
     * server-side log at ERROR level with the complete cause chain — this gives
     * operations teams the information they need to diagnose connectivity issues
     * with the Keycloak IDP.  None of this detail reaches the HTTP caller.
     * CONTROL: RN-10
     *
     * @param exchange the current server exchange
     * @param ex       the exception signalling IDP unavailability
     * @return a {@link Mono} completing when the response body has been written
     */
    // CONTROL: RN-10
    private Mono<Void> handleIdpUnavailable(ServerWebExchange exchange, Throwable ex) {
        // CONTROL: RN-10 — full detail logged internally at ERROR level;
        // caller receives only the generic 503 body with no internal detail.
        log.error("Identity Provider unreachable — rejecting request with HTTP 503. "
                + "Root cause: {}", rootCauseMessage(ex), ex);

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(BODY_SERVICE_UNAVAILABLE);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Extracts the message from the deepest non-null cause in the exception
     * chain, for inclusion in the internal log message only.
     *
     * <p>This value NEVER appears in the HTTP response body (CONTROL: RN-10).
     *
     * @param ex the top-level exception
     * @return the root cause message, or the top-level message if no deeper
     *         cause exists
     */
    private String rootCauseMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
