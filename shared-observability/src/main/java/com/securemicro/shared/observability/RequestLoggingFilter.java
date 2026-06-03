package com.securemicro.shared.observability;

// CONTROL: INI-16

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive WebFilter that provides structured request/response logging with
 * MDC-based context propagation for every HTTP exchange.
 *
 * <p>Responsibilities (per plan section 4.3):
 * <ol>
 *   <li>Generate or propagate {@code X-Correlation-ID} -- if the incoming
 *       request already carries the header the value is reused (after
 *       sanitization to prevent log injection); otherwise a new UUID is
 *       generated and the header is added to the response.</li>
 *   <li>Extract {@code auth.subject} from the reactive {@link SecurityContext}
 *       if the request is authenticated (JWT bearer token); otherwise the
 *       field is set to {@code anonymous}.</li>
 *   <li>Emit a single structured JSON log event on completion of the HTTP
 *       exchange containing all ten mandatory fields defined in plan 4.3:
 *       {@code timestamp}, {@code correlation-id}, {@code trace-id},
 *       {@code span-id}, {@code http.method}, {@code http.path},
 *       {@code http.status}, {@code duration_ms}, {@code auth.subject},
 *       {@code service.name}, {@code service.version}.</li>
 * </ol>
 *
 * <p>The structured JSON output is produced by the Logstash Logback Encoder
 * configured in {@code logback-spring.xml}; this class merely places the
 * domain fields into the MDC so the encoder can pick them up automatically.
 *
 * <h2>Timing</h2>
 * Wall-clock duration is measured with {@link System#nanoTime()} for
 * nanosecond resolution, then converted to milliseconds for the log field.
 *
 * <h2>OTel trace/span fields</h2>
 * When the OpenTelemetry Spring Boot starter is active, the OTel Java agent
 * automatically injects {@code trace_id} and {@code span_id} into the MDC
 * (as {@code trace_id} and {@code span_id}). This filter reads those MDC
 * keys and re-publishes them under the canonical names used by the log
 * schema ({@code trace-id} and {@code span-id}). If the OTel agent is absent
 * (graceful degradation -- AC INI-21) the fields are logged as {@code none}.
 *
 * <p>CONTROL: INI-16
 */
public class RequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** Header name for correlation-id propagation (plan section 5.3). */
    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** MDC key names -- must match the field names in plan section 4.3. */
    static final String MDC_CORRELATION_ID  = "correlation-id";
    static final String MDC_AUTH_SUBJECT    = "auth.subject";
    static final String MDC_HTTP_METHOD     = "http.method";
    static final String MDC_HTTP_PATH       = "http.path";
    static final String MDC_HTTP_STATUS     = "http.status";
    static final String MDC_DURATION_MS     = "duration_ms";
    static final String MDC_SERVICE_NAME    = "service.name";
    static final String MDC_SERVICE_VERSION = "service.version";
    static final String MDC_TRACE_ID        = "trace-id";
    static final String MDC_SPAN_ID         = "span-id";

    /** Keys injected by the OTel Java agent / SDK into the MDC. */
    private static final String OTEL_MDC_TRACE_ID = "trace_id";
    private static final String OTEL_MDC_SPAN_ID  = "span_id";

    private final ObservabilityProperties properties;

    /**
     * Constructs the filter with the resolved observability configuration.
     *
     * @param properties service name and version read from
     *                   {@code securemicro.observability.*} in
     *                   {@code application.yml}
     */
    public RequestLoggingFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    // ------------------------------------------------------------------ //
    //  WebFilter contract                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Intercepts every HTTP exchange, propagates/generates the
     * {@code X-Correlation-ID}, extracts the authenticated subject, and on
     * completion emits the structured log event.
     *
     * <p>The implementation follows the reactive contract: all side-effects
     * are attached as {@link Mono} operators so that the filter composes
     * correctly with the rest of the WebFlux filter chain.
     *
     * @param exchange the current server exchange
     * @param chain    the remaining filter chain
     * @return a {@link Mono} that signals completion of the exchange
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        final long startNanos = System.nanoTime();

        // ---------------------------------------------------------------- //
        //  Step 1 -- resolve correlation-id                                 //
        // ---------------------------------------------------------------- //
        final String correlationId = resolveCorrelationId(exchange);

        // Write the (possibly generated) correlation-id back on the response
        // so callers can correlate client-side traces with server-side logs.
        exchange.getResponse()
                .getHeaders()
                .add(CORRELATION_ID_HEADER, correlationId);

        // ---------------------------------------------------------------- //
        //  Step 2 -- extract auth.subject from the reactive SecurityContext  //
        // ---------------------------------------------------------------- //
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> {
                    if (auth instanceof JwtAuthenticationToken jwtToken) {
                        String subject = jwtToken.getToken().getSubject();
                        return subject != null ? subject : auth.getName();
                    }
                    return auth.getName();
                })
                .defaultIfEmpty("anonymous")
                // -------------------------------------------------------- //
                //  Step 3 -- run the rest of the filter chain, then log     //
                // -------------------------------------------------------- //
                .flatMap(subject ->
                        chain.filter(exchange)
                             .doFinally(signal -> emitLog(exchange, correlationId, subject, startNanos))
                );
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Reads {@code X-Correlation-ID} from the incoming request headers.
     * If the header is absent or blank a new UUID v4 is generated.
     *
     * <p>When the header is present, the raw value is sanitized before being
     * stored in the MDC to prevent log injection (OWASP A09 / CWE-117).
     * Control characters (including newline, carriage-return) are stripped and
     * the value is capped at 128 characters.
     *
     * @param exchange the current server exchange
     * @return the resolved and sanitized correlation-id string
     */
    private String resolveCorrelationId(ServerWebExchange exchange) {
        String fromHeader = exchange.getRequest()
                                    .getHeaders()
                                    .getFirst(CORRELATION_ID_HEADER);
        return (fromHeader != null && !fromHeader.isBlank())
                ? sanitizeCorrelationId(fromHeader)
                : UUID.randomUUID().toString();
    }

    /**
     * Sanitizes an externally supplied correlation-id value before it is
     * written to the MDC (log injection guard -- OWASP A09 / CWE-117).
     *
     * <p>Two transformations are applied:
     * <ol>
     *   <li>All Unicode control characters (U+0000-U+001F, U+007F-U+009F) are
     *       removed. This eliminates newline, carriage-return, tab, and other
     *       characters that could forge synthetic log lines in text appenders.</li>
     *   <li>The result is capped at 128 characters to bound MDC memory usage
     *       and prevent abnormally large values from bloating logs.</li>
     * </ol>
     *
     * @param raw the raw header value received from the HTTP client; must not be null
     * @return the sanitized, length-limited correlation-id
     */
    // CONTROL: INI-16
    static String sanitizeCorrelationId(String raw) {
        if (raw == null) return null;
        // Remove control characters: U+0000-U+001F (C0 controls) and U+007F-U+009F (DEL + C1 controls)
        String cleaned = raw.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "");
        // Cap length to prevent abnormally large MDC entries
        return cleaned.substring(0, Math.min(128, cleaned.length()));
    }

    /**
     * Constructs the MDC context and emits a single {@code INFO} log event
     * containing all ten mandatory fields from plan section 4.3.
     *
     * <p>The MDC is populated immediately before logging and cleared
     * immediately after to prevent context leakage across threads.
     *
     * @param exchange      the completed exchange (used to read method, path,
     *                      and response status)
     * @param correlationId the resolved or generated correlation-id
     * @param subject       the authenticated subject or {@code "anonymous"}
     * @param startNanos    the nanosecond timestamp captured before the chain
     *                      executed (see {@link System#nanoTime()})
     */
    private void emitLog(ServerWebExchange exchange,
                         String correlationId,
                         String subject,
                         long startNanos) {

        final long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        // Resolve OTel trace/span IDs from MDC if the agent has set them.
        final String traceId = resolveOtelField(OTEL_MDC_TRACE_ID);
        final String spanId  = resolveOtelField(OTEL_MDC_SPAN_ID);

        final String method  = exchange.getRequest().getMethod().name();
        final String path    = exchange.getRequest().getPath().value();
        final int    status  = resolveStatus(exchange);

        try {
            MDC.put(MDC_CORRELATION_ID,  correlationId);
            MDC.put(MDC_TRACE_ID,        traceId);
            MDC.put(MDC_SPAN_ID,         spanId);
            MDC.put(MDC_AUTH_SUBJECT,    subject);
            MDC.put(MDC_HTTP_METHOD,     method);
            MDC.put(MDC_HTTP_PATH,       path);
            MDC.put(MDC_HTTP_STATUS,     String.valueOf(status));
            MDC.put(MDC_DURATION_MS,     String.valueOf(durationMs));
            MDC.put(MDC_SERVICE_NAME,    properties.getServiceName());
            MDC.put(MDC_SERVICE_VERSION, properties.getServiceVersion());

            log.info("HTTP {} {} -> {} ({}ms) [{}]",
                    method, path, status, durationMs, correlationId);

        } finally {
            // Always clear to prevent leakage in thread-pool environments.
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_AUTH_SUBJECT);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_HTTP_PATH);
            MDC.remove(MDC_HTTP_STATUS);
            MDC.remove(MDC_DURATION_MS);
            MDC.remove(MDC_SERVICE_NAME);
            MDC.remove(MDC_SERVICE_VERSION);
        }
    }

    /**
     * Reads an OTel-injected MDC field, returning {@code "none"} if the
     * OTel agent is not active (graceful degradation -- AC INI-21).
     *
     * @param key the MDC key set by the OTel Java agent
     * @return the field value or {@code "none"} if absent
     */
    private String resolveOtelField(String key) {
        String value = MDC.get(key);
        return (value != null && !value.isBlank()) ? value : "none";
    }

    /**
     * Safely resolves the HTTP response status code.  Returns {@code 0} if
     * the response has not yet committed (should not happen in {@code doFinally},
     * but guards against edge cases).
     *
     * @param exchange the current exchange
     * @return the HTTP status code as an integer
     */
    private int resolveStatus(ServerWebExchange exchange) {
        try {
            var statusCode = exchange.getResponse().getStatusCode();
            return statusCode != null ? statusCode.value() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }
}
