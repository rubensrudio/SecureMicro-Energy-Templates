package com.securemicro.shared.observability;

// CONTROL: INI-17

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link AuditTrailService}.
 *
 * <p>Strategy: attach a Logback {@link ListAppender} to the logger used by
 * {@link AuditTrailService} and assert on the captured {@link ILoggingEvent}.
 * The MDC map on each captured event carries the {@code audit.*} fields that
 * the {@link net.logstash.logback.encoder.LogstashEncoder} would include in
 * the JSON output at production runtime.
 *
 * <p>Tests run without a Spring context — {@link AuditTrailService} is
 * instantiated directly so the suite is fast and has no external dependencies.
 *
 * <p>CONTROL: INI-17
 */
class AuditTrailServiceTest {

    // ------------------------------------------------------------------ //
    //  MDC key constants (mirror of AuditTrailService — kept package-     //
    //  private so both classes share the same namespace).                  //
    // ------------------------------------------------------------------ //

    static final String MDC_EVENT_TYPE      = "event-type";
    static final String MDC_AUDIT_SUBJECT   = "audit.subject";
    static final String MDC_AUDIT_ACTION    = "audit.action";
    static final String MDC_AUDIT_RESOURCE  = "audit.resource";
    static final String MDC_AUDIT_RESULT    = "audit.result";
    static final String MDC_AUDIT_REASON    = "audit.reason";

    // ------------------------------------------------------------------ //
    //  Logback in-memory capture                                          //
    // ------------------------------------------------------------------ //

    private ListAppender<ILoggingEvent> listAppender;
    private Logger auditLogger;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger(AuditTrailService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);
        // Ensure MDC is clean before each test so tests are fully isolated.
        MDC.clear();
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(listAppender);
        listAppender.stop();
        // Guarantee MDC is clean regardless of test outcome.
        MDC.clear();
    }

    // ------------------------------------------------------------------ //
    //  Helper                                                              //
    // ------------------------------------------------------------------ //

    /** Returns a fresh {@link AuditTrailService} with no dependencies. */
    private AuditTrailService buildService() {
        return new AuditTrailService();
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("audit() emits exactly one log event with event-type=AUDIT")
    void audit_emitsExactlyOneEventWithEventTypeAudit() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act ------------------------------------------------------ //
        service.audit("user-123", "READ", "/api/v1/meters/99", "SUCCESS", null);

        // ---- Assert -- exactly one event captured --------------------- //
        assertThat(listAppender.list).hasSize(1);

        ILoggingEvent event = listAppender.list.get(0);

        // The event-type field must be "AUDIT" (set via MDC and captured
        // on the event's MDC map by Logback before the logger call returns).
        assertThat(event.getMDCPropertyMap())
                .containsEntry(MDC_EVENT_TYPE, "AUDIT");
    }

    @Test
    @DisplayName("audit() populates all five audit.* MDC fields on the captured event")
    void audit_populatesAllFiveAuditFields() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act ------------------------------------------------------ //
        service.audit(
                "service-account-A",
                "WRITE",
                "/api/v1/meters/42",
                "SUCCESS",
                "meter reading update"
        );

        // ---- Assert --------------------------------------------------- //
        assertThat(listAppender.list).hasSize(1);
        var mdc = listAppender.list.get(0).getMDCPropertyMap();

        assertThat(mdc).containsEntry(MDC_AUDIT_SUBJECT,  "service-account-A");
        assertThat(mdc).containsEntry(MDC_AUDIT_ACTION,   "WRITE");
        assertThat(mdc).containsEntry(MDC_AUDIT_RESOURCE, "/api/v1/meters/42");
        assertThat(mdc).containsEntry(MDC_AUDIT_RESULT,   "SUCCESS");
        assertThat(mdc).containsEntry(MDC_AUDIT_REASON,   "meter reading update");
    }

    @Test
    @DisplayName("audit() with result=null does not throw NullPointerException")
    void audit_nullResult_noNpe() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act + Assert -- no exception must propagate -------------- //
        assertThatNoException().isThrownBy(() ->
                service.audit("admin", "DELETE", "/api/v1/meters/7", null, "test edge case")
        );

        // One event must still be captured (null result logs as "null").
        assertThat(listAppender.list).hasSize(1);
        var mdc = listAppender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry(MDC_AUDIT_RESULT, "null");
    }

    @Test
    @DisplayName("audit() with all-null parameters does not throw NullPointerException")
    void audit_allNullParameters_noNpe() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act + Assert --------------------------------------------- //
        assertThatNoException().isThrownBy(() ->
                service.audit(null, null, null, null, null)
        );

        assertThat(listAppender.list).hasSize(1);
        var mdc = listAppender.list.get(0).getMDCPropertyMap();

        // All null parameters must be stored as the literal string "null".
        assertThat(mdc).containsEntry(MDC_AUDIT_SUBJECT,  "null");
        assertThat(mdc).containsEntry(MDC_AUDIT_ACTION,   "null");
        assertThat(mdc).containsEntry(MDC_AUDIT_RESOURCE, "null");
        assertThat(mdc).containsEntry(MDC_AUDIT_RESULT,   "null");
        assertThat(mdc).containsEntry(MDC_AUDIT_REASON,   "null");
    }

    @Test
    @DisplayName("audit() MDC is fully cleared after the method returns (no leakage between calls)")
    void audit_mdcClearedAfterMethodReturns() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act: first call ------------------------------------------ //
        service.audit("user-A", "READ", "/api/v1/resources/1", "SUCCESS", null);

        // ---- Assert: MDC must not retain any audit.* key after return -- //
        // The fields set inside audit() must be removed in the finally block.
        assertThat(MDC.get(MDC_EVENT_TYPE))     .isNull();
        assertThat(MDC.get(MDC_AUDIT_SUBJECT))  .isNull();
        assertThat(MDC.get(MDC_AUDIT_ACTION))   .isNull();
        assertThat(MDC.get(MDC_AUDIT_RESOURCE)) .isNull();
        assertThat(MDC.get(MDC_AUDIT_RESULT))   .isNull();
        assertThat(MDC.get(MDC_AUDIT_REASON))   .isNull();

        // ---- Act: second call after MDC cleared ----------------------- //
        service.audit("user-B", "WRITE", "/api/v1/resources/2", "FAILURE", "quota exceeded");

        // ---- Assert: two independent events, no cross-contamination --- //
        assertThat(listAppender.list).hasSize(2);

        var firstMdc  = listAppender.list.get(0).getMDCPropertyMap();
        var secondMdc = listAppender.list.get(1).getMDCPropertyMap();

        assertThat(firstMdc).containsEntry(MDC_AUDIT_SUBJECT, "user-A");
        assertThat(secondMdc).containsEntry(MDC_AUDIT_SUBJECT, "user-B");

        // After the second call MDC must also be clean.
        assertThat(MDC.get(MDC_AUDIT_SUBJECT)).isNull();
    }

    @Test
    @DisplayName("audit() propagates pre-existing correlation-id from MDC into the event")
    void audit_existingCorrelationId_includedInEvent() {

        // ---- Arrange: place a correlation-id in the MDC as the          //
        //               RequestLoggingFilter would do upstream.            //
        MDC.put("correlation-id", "cid-test-99");

        AuditTrailService service = buildService();

        // ---- Act ------------------------------------------------------ //
        service.audit("op-user", "READ", "/api/v1/meters/1", "SUCCESS", null);

        // ---- Assert -- correlation-id present on the event MDC snapshot //
        assertThat(listAppender.list).hasSize(1);
        var mdc = listAppender.list.get(0).getMDCPropertyMap();

        assertThat(mdc).containsEntry("correlation-id", "cid-test-99");

        // Clean up the correlation-id we placed manually.
        MDC.remove("correlation-id");
    }

    @Test
    @DisplayName("audit() log level is INFO")
    void audit_logLevelIsInfo() {

        // ---- Arrange -------------------------------------------------- //
        AuditTrailService service = buildService();

        // ---- Act ------------------------------------------------------ //
        service.audit("admin", "DELETE", "/api/v1/resources/5", "SUCCESS", null);

        // ---- Assert --------------------------------------------------- //
        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.get(0).getLevel())
                .isEqualTo(ch.qos.logback.classic.Level.INFO);
    }
}
