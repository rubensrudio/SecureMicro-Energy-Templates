package com.securemicro.shared.controls;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GlobalExceptionHandler}.
 *
 * <p>This class contains two groups of tests:
 * <ol>
 *   <li><b>Unit-style slice tests</b> (using {@code @WebFluxTest}) — verify
 *       that a {@link RuntimeException} results in HTTP 500 with
 *       {@code {"error":"internal_error"}} and no stack trace in the body.</li>
 *   <li><b>Integration test with {@link SecurityHeadersFilter}</b> — verifies
 *       that error responses (HTTP 500) ALSO carry all four mandatory security
 *       headers, satisfying the criterion "every response contains the 4
 *       security headers" (plan section 5.3, CONTROL: RN-10).</li>
 * </ol>
 *
 * <p>CONTROL: RN-10 — no stack trace in body; security headers on every response.
 * <p>CONTROL: INI-04 — uniform HTTP error response format.
 */
// CONTROL: RN-10
// CONTROL: INI-04
@WebFluxTest
@Import({SecurityHeadersFilter.class, GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // HTTP status tests
    // ------------------------------------------------------------------

    @Test
    void runtimeExceptionShouldReturn500() {
        webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectStatus().isEqualTo(500);
    }

    // ------------------------------------------------------------------
    // Response body tests
    // ------------------------------------------------------------------

    @Test
    void runtimeExceptionBodyShouldContainGenericError() {
        webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    Map<?, ?> body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("error")).isEqualTo("internal_error");
                });
    }

    @Test
    void runtimeExceptionBodyShouldNotContainStackTrace() throws Exception {
        byte[] rawBody = webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(rawBody).isNotNull();
        String bodyAsString = new String(rawBody);

        // CONTROL: RN-10 — stack trace NEVER exposed in HTTP response
        assertThat(bodyAsString).doesNotContain("stackTrace");
        assertThat(bodyAsString).doesNotContain("stack_trace");
    }

    @Test
    void runtimeExceptionBodyShouldNotContainClassReferences() {
        webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    // CONTROL: RN-10 — class references (e.g. "at com.") must never appear
                    assertThat(body).doesNotContain("at com.");
                    assertThat(body).doesNotContain("java.lang.RuntimeException");
                });
    }

    @Test
    void errorResponseContentTypeShouldBeJson() {
        webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    // ------------------------------------------------------------------
    // Integration test: SecurityHeadersFilter + GlobalExceptionHandler
    //
    // Verifies that error responses (HTTP 500) carry ALL four mandatory
    // security headers — proving that SecurityHeadersFilter runs before
    // GlobalExceptionHandler and its headers are not stripped by the
    // exception-handling path.
    //
    // CONTROL: RN-10 — security headers present on every response,
    //                   including error responses.
    // ------------------------------------------------------------------

    @Test
    void errorResponseShouldContainAllFourSecurityHeaders() {
        // CONTROL: RN-10 — all four security headers on every response,
        // including HTTP 500 responses produced by GlobalExceptionHandler
        webTestClient.get()
                .uri("/bomb")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader()
                .valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader()
                .valueEquals("X-Frame-Options", "DENY")
                .expectHeader()
                .valueEquals("Content-Security-Policy", "default-src 'none'")
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    // CONTROL: RN-10 — body must be {"error":"internal_error"}, no stackTrace
                    assertThat(body).contains("\"error\"");
                    assertThat(body).contains("internal_error");
                    assertThat(body).doesNotContain("stackTrace");
                    assertThat(body).doesNotContain("stack_trace");
                    assertThat(body).doesNotContain("\"exception\"");
                    assertThat(body).doesNotContain("\"trace\"");
                });
    }

    // ------------------------------------------------------------------
    // Inner test configuration — provides the test controller and
    // registers SecurityHeadersFilter as a bean in the WebFlux context
    // so that WebFilterChain includes it for all requests.
    // ------------------------------------------------------------------

    /**
     * Minimal Spring configuration used exclusively in this test class.
     *
     * <p>Provides the {@link BombController} endpoint that always throws
     * a {@link RuntimeException}, allowing tests to exercise both
     * {@link SecurityHeadersFilter} and {@link GlobalExceptionHandler}
     * in the same WebFlux filter chain.
     */
    @Configuration
    static class TestConfig {

        @Bean
        BombController bombController() {
            return new BombController();
        }
    }

    /**
     * Test-only REST controller that always throws {@link RuntimeException}
     * to exercise the {@link GlobalExceptionHandler} catch-all path.
     */
    @RestController
    static class BombController {

        @GetMapping("/bomb")
        Mono<String> bomb() {
            // Intentional — simulates an unhandled RuntimeException
            throw new RuntimeException("simulated unhandled error");
        }
    }
}
