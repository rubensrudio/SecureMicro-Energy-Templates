package com.securemicro.shared.controls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Global exception handler that converts unhandled exceptions into safe,
 * generic HTTP error responses — never exposing stack traces to callers.
 *
 * <p>Every error response follows the format defined in plan section 5.2:
 * <pre>{"error": "&lt;code&gt;"}</pre>
 *
 * <p>Full exception details (message, class, stack trace) are written to the
 * application log for post-incident analysis, but are NEVER included in the
 * HTTP response body. This satisfies RN-10 and prevents inadvertent leakage
 * of internal implementation details via error responses.
 *
 * <p>This class is intentionally NOT annotated with {@code @Component} to
 * avoid double-registration when the auto-configuration registers the bean
 * via {@link SharedControlsAutoConfiguration#globalExceptionHandler()}.
 * Consuming services may override it by declaring their own
 * {@code GlobalExceptionHandler} bean — the {@code @ConditionalOnMissingBean}
 * guard in the auto-configuration ensures only one instance is active.
 *
 * <p>CONTROL: RN-10 — global exception handling prevents stack trace exposure.
 * <p>CONTROL: INI-04 — uniform HTTP error responses (4xx/5xx).
 *
 * @see SharedControlsAutoConfiguration
 */
// CONTROL: RN-10
// CONTROL: INI-04
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_KEY          = "error";
    private static final String INTERNAL_ERROR_CODE = "internal_error";

    // -------------------------------------------------------------------------

    /**
     * Re-throws {@link AccessDeniedException} so that Spring Security's
     * {@code ServerAccessDeniedHandler} (configured in
     * {@code IdentitySecurityConfiguration}) can handle it and return HTTP 403
     * with the generic body {@code {"error":"forbidden"}}.
     *
     * <p>Without this re-throw the catch-all {@link RuntimeException} handler
     * below would intercept the exception first and return HTTP 500, bypassing
     * the security handler and violating CONTROL: INI-05.
     *
     * <p>CONTROL: INI-05 — 403 must be returned for insufficient roles, not 500.
     *
     * @param ex the access denied exception thrown by {@code @PreAuthorize}
     * @throws AccessDeniedException always — delegates to Spring Security
     */
    // CONTROL: INI-05
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException ex) throws AccessDeniedException {
        // Re-throw so Spring Security's ServerAccessDeniedHandler returns 403.
        throw ex;
    }

    /**
     * Catches any {@link RuntimeException} not handled by a more specific
     * handler and returns HTTP 500 with a generic error body.
     *
     * <p>The exception is logged at ERROR level (including stack trace) for
     * operational visibility, but the stack trace is NEVER included in the
     * HTTP response — CONTROL: RN-10.
     *
     * @param ex the uncaught runtime exception
     * @return 500 response with {@code {"error":"internal_error"}}
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        // CONTROL: RN-10 — log internally, never expose stack trace in response
        log.error("Unhandled RuntimeException: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, INTERNAL_ERROR_CODE));
    }

    /**
     * Catch-all handler for any {@link Exception} not matched by a more
     * specific handler (e.g. checked exceptions propagated through reactive
     * pipelines).
     *
     * @param ex the uncaught exception
     * @return 500 response with {@code {"error":"internal_error"}}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        // CONTROL: RN-10 — log internally, never expose stack trace in response
        log.error("Unhandled Exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, INTERNAL_ERROR_CODE));
    }
}
