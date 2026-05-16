package com.securemicro.shared.controls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityHeadersFilter}.
 *
 * <p>Verifies that all four mandatory security headers defined in plan
 * section 5.3 are injected into every HTTP response — CONTROL: RN-10.
 *
 * <p>Tests use Spring's {@link MockServerWebExchange} to avoid starting
 * a full WebFlux context, keeping each test fast and deterministic.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }

    // ------------------------------------------------------------------
    // Individual header assertions
    // ------------------------------------------------------------------

    @Test
    void shouldAddStrictTransportSecurityHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, noOpChain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void shouldAddXContentTypeOptionsHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, noOpChain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    @Test
    void shouldAddXFrameOptionsHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, noOpChain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
                .isEqualTo("DENY");
    }

    @Test
    void shouldAddContentSecurityPolicyHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, noOpChain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'");
    }

    // ------------------------------------------------------------------
    // Compound assertion — all four headers in a single call
    // ------------------------------------------------------------------

    @Test
    void shouldAddAllFourSecurityHeadersInSingleCall() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any").build());

        filter.filter(exchange, noOpChain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
                .isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
                .isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                .isNotBlank();
    }

    // ------------------------------------------------------------------
    // Delegation test — filter must call chain.filter()
    // ------------------------------------------------------------------

    @Test
    void shouldDelegateToNextFilterInChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        boolean[] chainInvoked = {false};
        WebFilterChain chain = ex -> {
            chainInvoked[0] = true;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(chainInvoked[0]).isTrue();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WebFilterChain noOpChain() {
        return ex -> Mono.empty();
    }
}
