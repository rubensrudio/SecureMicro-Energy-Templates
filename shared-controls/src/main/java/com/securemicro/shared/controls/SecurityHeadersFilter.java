package com.securemicro.shared.controls;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that injects mandatory HTTP security headers into every response.
 *
 * <p>Headers injected on every outbound response (plan section 5.3):
 * <ul>
 *   <li>{@code Strict-Transport-Security: max-age=31536000; includeSubDomains}
 *       — HSTS, prevents protocol-downgrade attacks.</li>
 *   <li>{@code X-Content-Type-Options: nosniff}
 *       — disables MIME-type sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY}
 *       — blocks click-jacking via iframe embedding.</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'}
 *       — restrictive CSP baseline; consuming services may override.</li>
 * </ul>
 *
 * <p>This class is intentionally NOT annotated with {@code @Component} to
 * avoid double-registration when the auto-configuration registers the bean
 * via {@link SharedControlsAutoConfiguration#securityHeadersFilter()}.
 * The consuming application may replace this bean by declaring its own
 * {@code SecurityHeadersFilter} bean — the
 * {@code @ConditionalOnMissingBean} guard in the auto-configuration ensures
 * only one instance is active at runtime (safe for TASK-013 integration).
 *
 * <p>CONTROL: RN-10 — security headers prevent information leakage and
 * enforce transport security.
 *
 * @see SharedControlsAutoConfiguration
 */
// CONTROL: RN-10
public class SecurityHeadersFilter implements WebFilter {

    // --- Header names --------------------------------------------------------

    private static final String HEADER_HSTS               = "Strict-Transport-Security";
    private static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_FRAME_OPTIONS       = "X-Frame-Options";
    private static final String HEADER_CSP                 = "Content-Security-Policy";

    // --- Header values -------------------------------------------------------

    private static final String VALUE_HSTS                = "max-age=31536000; includeSubDomains";
    private static final String VALUE_CONTENT_TYPE_OPTIONS = "nosniff";
    private static final String VALUE_FRAME_OPTIONS        = "DENY";
    private static final String VALUE_CSP                  = "default-src 'none'";

    // -------------------------------------------------------------------------

    /**
     * Adds all four security headers before delegating to the next filter.
     *
     * <p>Headers are set on the response BEFORE {@code chain.filter(exchange)}
     * is called so that they are present regardless of whether the downstream
     * handler writes the body or not.
     *
     * @param exchange current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return {@code Mono<Void>} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // CONTROL: RN-10 — inject all four mandatory security headers
        exchange.getResponse().getHeaders().set(HEADER_HSTS,                VALUE_HSTS);
        exchange.getResponse().getHeaders().set(HEADER_CONTENT_TYPE_OPTIONS, VALUE_CONTENT_TYPE_OPTIONS);
        exchange.getResponse().getHeaders().set(HEADER_FRAME_OPTIONS,        VALUE_FRAME_OPTIONS);
        exchange.getResponse().getHeaders().set(HEADER_CSP,                  VALUE_CSP);

        return chain.filter(exchange);
    }
}
