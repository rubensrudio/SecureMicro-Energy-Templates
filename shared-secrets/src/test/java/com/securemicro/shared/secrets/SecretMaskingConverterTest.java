package com.securemicro.shared.secrets;

// CONTROL: RN-01 — tests verify that secrets are masked, never logged in clear text
// CONTROL: INI-11 — zero secrets visible in log output

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecretMaskingConverter}.
 *
 * <p>Strategy: construct a {@link LoggingEvent} directly (bypassing the full
 * Logback pipeline) so that tests are fast and hermetic — no running appender,
 * no file system, no network.  The converter is exercised via its public API
 * {@code convert(ILoggingEvent)}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Bearer token masking (AC pattern 1)</li>
 *   <li>Password masking (AC pattern 2)</li>
 *   <li>Client-secret masking (AC pattern 3)</li>
 *   <li>Vault token masking (AC pattern 4)</li>
 *   <li>Regression: ordinary log messages are never modified</li>
 *   <li>Regression: non-secret key=value pairs are never masked</li>
 *   <li>Null message safety — no NPE</li>
 * </ul>
 *
 * CONTROL: RN-01 | INI-11
 */
@DisplayName("SecretMaskingConverter")
class SecretMaskingConverterTest {

    private static final String MASKED = "***MASKED***";

    private SecretMaskingConverter converter;

    /** The logger instance is needed only to build a valid {@link LoggingEvent}. */
    private static final Logger TEST_LOGGER =
            (Logger) LoggerFactory.getLogger(SecretMaskingConverterTest.class);

    @BeforeEach
    void setUp() {
        converter = new SecretMaskingConverter();
        converter.start();
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Creates a {@link LoggingEvent} whose {@code formattedMessage} is exactly
     * {@code message}.  Using a pre-formatted message (no args) avoids
     * interaction with the SLF4J argument substitution machinery.
     */
    private ILoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName(TEST_LOGGER.getName());
        event.setLevel(Level.INFO);
        // setMessage sets the raw message; setArgumentArray(null) keeps it as-is
        // so getFormattedMessage() returns the raw message unchanged.
        event.setMessage(message);
        return event;
    }

    // =========================================================================
    // Pattern 1 — Bearer token
    // =========================================================================

    @Test
    @DisplayName("masks Bearer token with full JWT structure")
    void masksFullJwtBearerToken() {
        // Arrange — realistic JWT (header.payload.signature)
        String input = "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.signature";

        // Act
        String result = converter.convert(event(input));

        // Assert
        assertThat(result)
                .contains("Authorization: Bearer " + MASKED)
                .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
    }

    @Test
    @DisplayName("masks Bearer token that is exactly 8 characters (boundary)")
    void masksBearerTokenAtEightCharBoundary() {
        String input = "Bearer abcdefgh";

        String result = converter.convert(event(input));

        assertThat(result).isEqualTo("Bearer " + MASKED);
    }

    @Test
    @DisplayName("does NOT mask Bearer prefix followed by fewer than 8 characters")
    void doesNotMaskShortBearerValue() {
        // 7-char value: should not be masked (too short to be a real token)
        String input = "Bearer short12";

        String result = converter.convert(event(input));

        // "short12" is 7 chars — below the 8-char threshold; must not be masked
        assertThat(result).isEqualTo(input);
    }

    // =========================================================================
    // Pattern 2 — Password
    // =========================================================================

    @Test
    @DisplayName("masks password=value (equals sign, lower-case key)")
    void masksPasswordWithEquals() {
        String input = "password=secret123";

        String result = converter.convert(event(input));

        assertThat(result)
                .isEqualTo("password=" + MASKED)
                .doesNotContain("secret123");
    }

    @Test
    @DisplayName("masks password:value (colon separator)")
    void masksPasswordWithColon() {
        // "password: supersecret" — group 1 captures "password: " (with the space)
        // so the replacement is "password: ***MASKED***", not "password:***MASKED***"
        String input = "password: supersecret";

        String result = converter.convert(event(input));

        // The space after the colon is part of group 1 (\\s* in the pattern)
        // and is therefore preserved in the output.
        assertThat(result)
                .contains("password: " + MASKED)
                .doesNotContain("supersecret");
    }

    @Test
    @DisplayName("masks PASSWORD=value (upper-case key — case-insensitive)")
    void masksPasswordCaseInsensitive() {
        String input = "PASSWORD=TopSecret999";

        String result = converter.convert(event(input));

        assertThat(result).doesNotContain("TopSecret999");
        assertThat(result).contains(MASKED);
    }

    // =========================================================================
    // Pattern 3 — Client secret
    // =========================================================================

    @Test
    @DisplayName("masks client_secret=value (underscore separator)")
    void masksClientSecretWithUnderscore() {
        String input = "client_secret=abc123def456";

        String result = converter.convert(event(input));

        assertThat(result)
                .contains("client_secret=" + MASKED)
                .doesNotContain("abc123def456");
    }

    @Test
    @DisplayName("masks client.secret=value (dot separator)")
    void masksClientSecretWithDot() {
        String input = "client.secret=xyz789";

        String result = converter.convert(event(input));

        assertThat(result)
                .contains("client.secret=" + MASKED)
                .doesNotContain("xyz789");
    }

    @Test
    @DisplayName("masks client_secret:value (colon separator with trailing space)")
    void masksClientSecretWithColon() {
        // Group 1 captures "client_secret: " (key + colon + space) — space is preserved
        String input = "client_secret: myOAuthSecret";

        String result = converter.convert(event(input));

        assertThat(result)
                .contains("client_secret: " + MASKED)
                .doesNotContain("myOAuthSecret");
    }

    // =========================================================================
    // Pattern 4 — Vault token (hvs.*)
    // =========================================================================

    @Test
    @DisplayName("masks Vault service token (hvs. prefix)")
    void masksVaultServiceToken() {
        String input = "hvs.AAAAAQKsomeVaultToken";

        String result = converter.convert(event(input));

        assertThat(result)
                .isEqualTo(MASKED)
                .doesNotContain("hvs.");
    }

    @Test
    @DisplayName("masks Vault token embedded in a longer message")
    void masksVaultTokenEmbeddedInMessage() {
        String input = "Resolved vault token: hvs.CAESILONGTOKENVALUE123 from env";

        String result = converter.convert(event(input));

        assertThat(result)
                .contains(MASKED)
                .doesNotContain("hvs.CAESILONGTOKENVALUE123");
    }

    @Test
    @DisplayName("does NOT mask hvs. prefix with fewer than 8 chars after the dot")
    void doesNotMaskShortHvsValue() {
        // "hvs.abc" — only 3 chars after prefix (below threshold)
        String input = "Some key hvs.abc end";

        String result = converter.convert(event(input));

        assertThat(result).isEqualTo(input);
    }

    // =========================================================================
    // Regression — non-secret messages must not be modified
    // =========================================================================

    @Test
    @DisplayName("does NOT modify a plain log message with no secret patterns")
    void doesNotModifyPlainMessage() {
        String input = "Normal log message without secrets";

        String result = converter.convert(event(input));

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("does NOT mask userId=value (non-secret key=value pair)")
    void doesNotMaskNonSecretKeyValue() {
        String input = "userId=12345";

        String result = converter.convert(event(input));

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("does NOT mask ordinary status log lines")
    void doesNotMaskStatusLine() {
        String input = "Request completed: GET /api/v1/resources status=200 duration_ms=42";

        String result = converter.convert(event(input));

        assertThat(result).isEqualTo(input);
    }

    // =========================================================================
    // Null safety
    // =========================================================================

    @Test
    @DisplayName("handles null message without throwing NPE")
    void handlesNullMessageWithoutNpe() {
        // Arrange — event with null message
        ILoggingEvent nullEvent = event(null);

        // Act — must not throw
        String result = converter.convert(nullEvent);

        // Assert — returns empty string or null, but no exception
        assertThat(result).isNullOrEmpty();
    }

    // =========================================================================
    // Multiple patterns in one message
    // =========================================================================

    @Test
    @DisplayName("masks multiple secrets in a single log message")
    void masksMultipleSecretsInOneMessage() {
        String input = "password=p@ssw0rd and client_secret=topSecretValue123";

        String result = converter.convert(event(input));

        assertThat(result)
                .contains("password=" + MASKED)
                .contains("client_secret=" + MASKED)
                .doesNotContain("p@ssw0rd")
                .doesNotContain("topSecretValue123");
    }
}
