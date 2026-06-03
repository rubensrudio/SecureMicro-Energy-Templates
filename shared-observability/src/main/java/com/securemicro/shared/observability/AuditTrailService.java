package com.securemicro.shared.observability;

// CONTROL: INI-17

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Spring-managed service that emits structured audit-trail log events.
 *
 * <h2>Purpose (TASK-007 / INI-17)</h2>
 * Every time a service performs an action that modifies or accesses sensitive
 * data it must record an immutable audit event.  This service is the single
 * point responsible for producing those events in a form that the downstream
 * log aggregator (Elastic, Splunk, Loki) can ingest and index without any
 * additional parsing configuration.
 *
 * <h2>Audit event schema (plan section 4.3)</h2>
 * <pre>
 * {
 *   "timestamp":        "&lt;ISO-8601 UTC — emitted automatically by LogstashEncoder&gt;",
 *   "event-type":       "AUDIT",
 *   "correlation-id":   "&lt;current MDC value — may be null if upstream filter absent&gt;",
 *   "audit.subject":    "&lt;subject parameter&gt;",
 *   "audit.action":     "&lt;action parameter&gt;",
 *   "audit.resource":   "&lt;resource parameter&gt;",
 *   "audit.result":     "&lt;result parameter&gt;",
 *   "audit.reason":     "&lt;reason parameter — may be null&gt;"
 * }
 * </pre>
 *
 * <h2>MDC lifecycle</h2>
 * All {@code audit.*} MDC keys are set immediately before the log call and
 * removed in a {@code finally} block regardless of any exception.  This
 * guarantees there is no MDC leakage into subsequent log events on the same
 * thread — a critical requirement when the service is deployed in a thread-pool
 * environment such as Netty/WebFlux.
 *
 * <h2>Null safety</h2>
 * No parameter may cause a {@link NullPointerException}.  When a caller passes
 * {@code null}, the value is stored in the MDC as the literal string
 * {@code "null"} so that the log event is always well-formed and the field is
 * always present for the log aggregator.
 *
 * <h2>Registration</h2>
 * The bean is declared in {@link SharedObservabilityAutoConfiguration} with
 * {@code @ConditionalOnMissingBean(AuditTrailService.class)} so that
 * consuming services can override it with a custom implementation without
 * conflicting with the auto-configuration.
 *
 * <p>CONTROL: INI-17
 */
@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    // ------------------------------------------------------------------ //
    //  MDC key constants                                                   //
    // ------------------------------------------------------------------ //

    /** Fixed event-type marker that distinguishes audit events from request logs. */
    // CONTROL: INI-17
    static final String MDC_EVENT_TYPE      = "event-type";
    static final String MDC_AUDIT_SUBJECT   = "audit.subject";
    static final String MDC_AUDIT_ACTION    = "audit.action";
    static final String MDC_AUDIT_RESOURCE  = "audit.resource";
    static final String MDC_AUDIT_RESULT    = "audit.result";
    static final String MDC_AUDIT_REASON    = "audit.reason";

    private static final String EVENT_TYPE_AUDIT = "AUDIT";

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Emits a single structured audit-trail log event at {@code INFO} level.
     *
     * <p>The event is written to the SLF4J logger for this class and encoded
     * as JSON by the {@link net.logstash.logback.encoder.LogstashEncoder}
     * configured in {@code logback-spring.xml}.  The
     * {@code timestamp} (ISO-8601 UTC) is added automatically by the encoder.
     *
     * <p>Any pre-existing MDC values (e.g. {@code correlation-id} placed by
     * {@link RequestLoggingFilter}) are preserved and included in the event
     * output automatically by LogstashEncoder — this service does not remove
     * or overwrite them.
     *
     * <p>All {@code audit.*} keys added by this method are removed in the
     * {@code finally} block to prevent leakage.
     *
     * @param subject  who performed the action (JWT subject, service account,
     *                 etc.); {@code null} is stored as the literal string
     *                 {@code "null"}
     * @param action   what was done — e.g. {@code "READ"}, {@code "WRITE"},
     *                 {@code "DELETE"}; {@code null} stored as {@code "null"}
     * @param resource the target resource — e.g. {@code "/api/v1/meters/123"};
     *                 {@code null} stored as {@code "null"}
     * @param result   outcome of the action — e.g. {@code "SUCCESS"},
     *                 {@code "FAILURE"}, {@code "DENIED"}; {@code null}
     *                 stored as {@code "null"}
     * @param reason   optional human-readable explanation (used mainly on
     *                 failure/denial); {@code null} stored as {@code "null"}
     */
    // CONTROL: RN-04
    // CONTROL: INI-17
    public void audit(String subject,
                      String action,
                      String resource,
                      String result,
                      String reason) {

        // Coerce null parameters to the literal string "null" so that MDC
        // entries are always non-null (MDC.put rejects null values in many
        // SLF4J implementations) and the JSON event is always well-formed.
        final String safeSubject  = nullSafe(subject);
        final String safeAction   = nullSafe(action);
        final String safeResource = nullSafe(resource);
        final String safeResult   = nullSafe(result);
        final String safeReason   = nullSafe(reason);

        try {
            // Populate audit-specific MDC fields before emitting the event.
            // LogstashEncoder includes the full MDC map as top-level JSON fields.
            MDC.put(MDC_EVENT_TYPE,     EVENT_TYPE_AUDIT);
            MDC.put(MDC_AUDIT_SUBJECT,  safeSubject);
            MDC.put(MDC_AUDIT_ACTION,   safeAction);
            MDC.put(MDC_AUDIT_RESOURCE, safeResource);
            MDC.put(MDC_AUDIT_RESULT,   safeResult);
            MDC.put(MDC_AUDIT_REASON,   safeReason);

            // Single log statement — the message is intentionally brief
            // because all semantic data is in the MDC / JSON fields.
            log.info("AUDIT {} {} {} -> {} [{}]",
                    safeAction, safeResource, safeSubject, safeResult, safeReason);

        } finally {
            // Always remove every key added by this method to prevent MDC
            // leakage into subsequent log events on the same thread.
            MDC.remove(MDC_EVENT_TYPE);
            MDC.remove(MDC_AUDIT_SUBJECT);
            MDC.remove(MDC_AUDIT_ACTION);
            MDC.remove(MDC_AUDIT_RESOURCE);
            MDC.remove(MDC_AUDIT_RESULT);
            MDC.remove(MDC_AUDIT_REASON);
        }
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Returns the string value itself if non-null, or the literal
     * {@code "null"} string if the argument is {@code null}.
     *
     * <p>This prevents {@link NullPointerException} in
     * {@link MDC#put(String, String)} and ensures every audit field is
     * always present in the JSON output.
     *
     * @param value the string to coerce
     * @return a non-null string representation
     */
    private static String nullSafe(String value) {
        return value != null ? value : "null";
    }
}
