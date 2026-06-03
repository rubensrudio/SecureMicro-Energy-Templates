package com.securemicro.shared.observability;

// CONTROL: INI-16

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestLoggingFilter}.
 *
 * <p>Strategy: attach a Logback {@link ListAppender} to the logger used by
 * {@link RequestLoggingFilter}, exercise the filter with
 * {@link MockServerHttpRequest} / {@link MockServerWebExchange}, and then
 * re-encode the captured {@link ILoggingEvent} with {@link LogstashEncoder}
 * to obtain the JSON payload.  This approach tests the real MDC population
 * logic without requiring a running Spring context.
 *
 * <p>The encoder is configured programmatically to rename {@code @timestamp}
 * to {@code timestamp} (matching the {@code <fieldNames>} directive in
 * {@code logback-spring.xml} and the schema in plan section 4.3).
 *
 * <p>CONTROL: INI-16
 */
class RequestLoggingFilterTest {

    // ------------------------------------------------------------------ //
    //  Logback in-memory capture                                          //
    // ------------------------------------------------------------------ //

    private ListAppender<ILoggingEvent> listAppender;
    private Logger filterLogger;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        filterLogger.addAppender(listAppender);
        MDC.clear();
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(listAppender);
        listAppender.stop();
        MDC.clear();
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /** Default {@link ObservabilityProperties} with known service name/version. */
    private ObservabilityProperties defaultProperties() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setServiceName("test-service");
        props.setServiceVersion("1.2.3");
        return props;
    }

    /**
     * Builds the filter-under-test with default properties.
     */
    private RequestLoggingFilter buildFilter() {
        return new RequestLoggingFilter(defaultProperties());
    }

    /**
     * Runs the filter with the given exchange and an inline chain that sets
     * the mock response status and then completes.
     *
     * @param filter   the filter to exercise
     * @param exchange the mock exchange
     * @param status   the HTTP status the "downstream" chain will set
     */
    private void runFilter(RequestLoggingFilter filter,
                           MockServerWebExchange exchange,
                           HttpStatus status) {
        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(status);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
    }

    /**
     * Serialises the captured log event to a JSON node using
     * {@link LogstashEncoder} so we can assert on the JSON payload that
     * would be written to stdout in a production deployment.
     *
     * <p>The encoder is configured to rename {@code @timestamp} to
     * {@code timestamp} and suppress {@code @version}, matching the
     * {@code <fieldNames>} directive in {@code logback-spring.xml}
     * (plan section 4.3 schema).
     *
     * @param event the captured Logback event
     * @return the JSON node produced by the encoder
     * @throws Exception if encoding or parsing fails
     */
    private JsonNode encodeToJson(ILoggingEvent event) throws Exception {
        // Mirror the <fieldNames> config in logback-spring.xml so tests
        // assert the same field names that production output emits.
        LogstashFieldNames fieldNames = new LogstashFieldNames();
        fieldNames.setTimestamp("timestamp");
        fieldNames.setVersion("[ignore]");

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setFieldNames(fieldNames);
        encoder.start();
        byte[] bytes = encoder.encode(event);
        encoder.stop();
        String json = new String(bytes, StandardCharsets.UTF_8).trim();
        // LogstashEncoder appends a newline; strip it before parsing.
        if (json.length() > 0 && json.charAt(json.length() - 1) == '\n') {
            json = json.substring(0, json.length() - 1);
        }
        return new ObjectMapper().readTree(json);
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                              //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("anonymous request: log is valid JSON containing all 10 mandatory fields")
    void anonymousRequest_emitsJsonWithAllMandatoryFields() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/api/v1/resources")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.OK);

        // ---- Assert --- one log event captured ------------------------ //
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);

        // Re-encode to JSON (simulates prod LogstashEncoder output)
        JsonNode json = encodeToJson(event);

        // 1. timestamp -- renamed from @timestamp via <fieldNames> in logback-spring.xml (plan 4.3)
        assertThat(json.has("timestamp")).isTrue();

        // 2. correlation-id -- from MDC
        assertThat(json.has("correlation-id")).isTrue();
        assertThat(json.get("correlation-id").asText()).isNotBlank();

        // 3. trace-id -- from MDC (no OTel agent in test -> "none")
        assertThat(json.has("trace-id")).isTrue();

        // 4. span-id -- from MDC (no OTel agent in test -> "none")
        assertThat(json.has("span-id")).isTrue();

        // 5. http.method
        assertThat(json.has("http.method")).isTrue();
        assertThat(json.get("http.method").asText()).isEqualTo("GET");

        // 6. http.path
        assertThat(json.has("http.path")).isTrue();
        assertThat(json.get("http.path").asText()).isEqualTo("/api/v1/resources");

        // 7. http.status
        assertThat(json.has("http.status")).isTrue();
        assertThat(json.get("http.status").asText()).isEqualTo("200");

        // 8. duration_ms -- must be present and numeric (>= 0)
        assertThat(json.has("duration_ms")).isTrue();
        assertThat(json.get("duration_ms").asLong()).isGreaterThanOrEqualTo(0L);

        // 9. auth.subject -- anonymous because no SecurityContext
        assertThat(json.has("auth.subject")).isTrue();
        assertThat(json.get("auth.subject").asText()).isEqualTo("anonymous");

        // 10. service.name
        assertThat(json.has("service.name")).isTrue();
        assertThat(json.get("service.name").asText()).isEqualTo("test-service");

        // 11. service.version (bonus -- also mandatory per schema)
        assertThat(json.has("service.version")).isTrue();
        assertThat(json.get("service.version").asText()).isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("authenticated request: auth.subject is populated from JWT sub claim")
    void authenticatedRequest_subjectFromJwt() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/api/v1/resources")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Build a minimal Jwt with a known subject.
        // JwtAuthenticationToken(jwt) without authorities sets isAuthenticated()=false,
        // so we pass at least one authority to get isAuthenticated()=true (Spring Security
        // AbstractAuthenticationToken contract).
        Jwt jwt = Jwt.withTokenValue("token")
                     .header("alg", "RS256")
                     .claim("sub", "user-uuid-123")
                     .issuedAt(Instant.now())
                     .expiresAt(Instant.now().plusSeconds(300))
                     .build();
        Authentication auth = new JwtAuthenticationToken(
                jwt,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_SERVICE_USER")));

        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.CREATED);
            return Mono.empty();
        };

        // ---- Act -- subscribe with a SecurityContext carrying the jwt -- //
        StepVerifier.create(
                filter.filter(exchange, chain)
                      .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        // ---- Assert --------------------------------------------------- //
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        assertThat(json.get("auth.subject").asText()).isEqualTo("user-uuid-123");
        assertThat(json.get("http.method").asText()).isEqualTo("POST");
        assertThat(json.get("http.path").asText()).isEqualTo("/api/v1/resources");
        assertThat(json.get("http.status").asText()).isEqualTo("201");
    }

    @Test
    @DisplayName("X-Correlation-ID propagated when present in request header")
    void correlationId_propagatedFromRequest() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        final String existingCid = "existing-cid-abc-123";

        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/api/v1/hello")
                .header(RequestLoggingFilter.CORRELATION_ID_HEADER, existingCid)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.OK);

        // ---- Assert -- MDC correlation-id matches the incoming header -- //
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        assertThat(json.get("correlation-id").asText()).isEqualTo(existingCid);

        // The filter must also echo the correlation-id in the response header.
        assertThat(exchange.getResponse()
                           .getHeaders()
                           .getFirst(RequestLoggingFilter.CORRELATION_ID_HEADER))
                .isEqualTo(existingCid);
    }

    @Test
    @DisplayName("X-Correlation-ID generated as UUID when absent from request")
    void correlationId_generatedWhenAbsent() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/api/v1/hello")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.OK);

        // ---- Assert -- correlation-id is a non-blank generated value --- //
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        String cid = json.get("correlation-id").asText();
        assertThat(cid).isNotBlank();

        // Must also appear on the response header
        assertThat(exchange.getResponse()
                           .getHeaders()
                           .getFirst(RequestLoggingFilter.CORRELATION_ID_HEADER))
                .isEqualTo(cid);
    }

    @Test
    @DisplayName("service.name and service.version reflect ObservabilityProperties values")
    void serviceMetadata_reflectedInLog() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        ObservabilityProperties customProps = new ObservabilityProperties();
        customProps.setServiceName("energy-metering-svc");
        customProps.setServiceVersion("2.0.0");
        RequestLoggingFilter filter = new RequestLoggingFilter(customProps);

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.OK);

        // ---- Assert --------------------------------------------------- //
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        assertThat(json.get("service.name").asText()).isEqualTo("energy-metering-svc");
        assertThat(json.get("service.version").asText()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("X-Correlation-ID with newline stripped -- log injection guard (OWASP A09 / CWE-117)")
    void correlationId_newlineInHeader_isStrippedFromMdc() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        // Simulate an attacker injecting a newline (0x0A) to forge a second log
        // line in text-based appenders. After sanitizeCorrelationId(), the value
        // stored in the MDC must not contain any control characters.
        final String maliciousHeader = "legit-id\nfake-log-line";

        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/api/v1/resources")
                .header(RequestLoggingFilter.CORRELATION_ID_HEADER, maliciousHeader)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.OK);

        // ---- Assert -- correlation-id in JSON log must NOT contain
        //               newline or carriage-return characters.            //
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        String cidInLog = json.get("correlation-id").asText();

        // The newline (0x0A) must have been stripped by sanitizeCorrelationId().
        assertThat(cidInLog).doesNotContain("\n");
        assertThat(cidInLog).doesNotContain("\r");

        // Length must be capped at 128 characters.
        assertThat(cidInLog.length()).isLessThanOrEqualTo(128);
    }

    @Test
    @DisplayName("log output is valid JSON parseable by ObjectMapper")
    void logOutput_isValidJson() throws Exception {

        // ---- Arrange -------------------------------------------------- //
        RequestLoggingFilter filter = buildFilter();

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.DELETE, "/api/v1/resources/42")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ---- Act ------------------------------------------------------ //
        runFilter(filter, exchange, HttpStatus.NO_CONTENT);

        // ---- Assert -- encodeToJson() already validates parseability,
        //               but we make it explicit here with an additional
        //               assertion on the root node type.
        assertThat(listAppender.list).hasSize(1);
        JsonNode json = encodeToJson(listAppender.list.get(0));

        // Root must be a JSON object (not array, null, or primitive)
        assertThat(json.isObject()).isTrue();

        // Verify all 10 mandatory schema fields are present (per plan 4.3)
        // "timestamp" is the renamed form of @timestamp -- see <fieldNames> in logback-spring.xml
        assertThat(json.has("timestamp")).isTrue();         // timestamp (plan 4.3)
        assertThat(json.has("correlation-id")).isTrue();   // correlation-id
        assertThat(json.has("trace-id")).isTrue();         // trace-id
        assertThat(json.has("span-id")).isTrue();          // span-id
        assertThat(json.has("http.method")).isTrue();      // http.method
        assertThat(json.has("http.path")).isTrue();        // http.path
        assertThat(json.has("http.status")).isTrue();      // http.status
        assertThat(json.has("duration_ms")).isTrue();      // duration_ms
        assertThat(json.has("auth.subject")).isTrue();     // auth.subject
        assertThat(json.has("service.name")).isTrue();     // service.name
        assertThat(json.has("service.version")).isTrue();  // service.version
    }
}
