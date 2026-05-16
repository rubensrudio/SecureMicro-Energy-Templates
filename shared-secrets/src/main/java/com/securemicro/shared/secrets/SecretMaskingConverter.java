package com.securemicro.shared.secrets;

// CONTROL: RN-01 — zero secrets in env vars, config files or log output
// CONTROL: INI-11 — zero secrets visible in version control or log streams

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback {@link ClassicConverter} that intercepts every log message and
 * replaces recognised secret patterns with {@code ***MASKED***}.
 *
 * <h2>Usage — registering in logback-spring.xml</h2>
 * <p>Add the following to the consumer service's {@code logback-spring.xml}
 * (e.g. in {@code tmpl-rest-api/src/main/resources/logback-spring.xml}):
 *
 * <pre>{@code
 * <!-- Register the converter once, near the top of logback-spring.xml -->
 * <conversionRule conversionWord="mask"
 *                 converterClass="com.securemicro.shared.secrets.SecretMaskingConverter"/>
 *
 * <!-- Use the %mask{} token in any pattern where masking is required -->
 * <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} - %mask{%msg}%n</pattern>
 * }</pre>
 *
 * <p>This converter is a <em>library</em> — it intentionally does NOT modify
 * the {@code logback-spring.xml} that lives in {@code shared-observability}.
 * The consumer module (e.g. {@code tmpl-rest-api} in TASK-014) decides whether
 * and where to activate masking.  This separation of concerns keeps
 * {@code shared-observability} independent of {@code shared-secrets}.
 *
 * <h2>Masked patterns</h2>
 * <ol>
 *   <li><strong>Bearer tokens</strong> —
 *       {@code Bearer <token>} where {@code <token>} is at least 8 characters
 *       composed of {@code [A-Za-z0-9._-]}.  Covers JWT and opaque tokens.
 *       Replacement: {@code Bearer ***MASKED***}.</li>
 *   <li><strong>Password values</strong> —
 *       {@code password=<value>} or {@code password: <value>} (case-insensitive).
 *       Replacement: {@code password=***MASKED***}.</li>
 *   <li><strong>Client-secret values</strong> —
 *       {@code client_secret=<value>} or {@code client.secret=<value>} or their
 *       colon variants (case-insensitive).
 *       Replacement: {@code client_secret=***MASKED***} / {@code client.secret=***MASKED***}.</li>
 *   <li><strong>HashiCorp Vault service tokens</strong> —
 *       {@code hvs.<token>} where {@code <token>} is at least 8 alphanumeric
 *       characters.  Covers the {@code hvs.*} service token format introduced
 *       in Vault 1.10+.  Replacement: {@code ***MASKED***}.</li>
 * </ol>
 *
 * <h2>Non-masked patterns</h2>
 * <p>The regexes are intentionally specific to avoid false positives.
 * Ordinary key=value pairs such as {@code userId=12345} or
 * {@code status=200} are <em>never</em> masked.
 *
 * <h2>Null safety</h2>
 * <p>A {@code null} or empty formatted message is returned as-is (empty
 * string) without throwing {@link NullPointerException}.
 *
 * <p>CONTROL: RN-01 — zero secrets in env vars, config files, VCS or logs.
 * <p>CONTROL: INI-11 — zero secrets in text-clear in any versioned artifact.
 *
 * @see ClassicConverter
 */
// CONTROL: RN-01
// CONTROL: INI-11
public class SecretMaskingConverter extends ClassicConverter {

    /** Replacement token used for all masked values. */
    private static final String MASK_LABEL = "***MASKED***";

    /**
     * Ordered list of patterns to apply against every formatted log message.
     *
     * <p>Design rules:
     * <ul>
     *   <li>Each pattern uses a capturing group for the <em>non-secret prefix</em>
     *       (group 1) so that the replacement can preserve the key while replacing
     *       only the secret value — e.g. {@code password=***MASKED***} instead of
     *       just {@code ***MASKED***}.</li>
     *   <li>Patterns with no key prefix (Vault {@code hvs.*} tokens) have no
     *       capturing group; the entire match is replaced with {@code ***MASKED***}.</li>
     *   <li>Minimum token length of 8 characters prevents masking of obviously
     *       non-sensitive short strings.</li>
     * </ul>
     *
     * CONTROL: RN-01 | INI-11
     */
    // CONTROL: RN-01
    // CONTROL: INI-11
    private static final List<Pattern> SECRET_PATTERNS = List.of(

            /*
             * Pattern 1 — Bearer tokens
             *
             * Matches:  "Bearer eyJhbGciOiJSUzI1NiJ9.payload.signature"
             * Group 1:  "Bearer "
             * Group 2:  the token value (replaced with ***MASKED***)
             *
             * Token characters: A-Za-z0-9, dot, underscore, hyphen — covers
             * both JWT (base64url with dots) and opaque bearer tokens.
             * Minimum length: 8 characters (excludes trivially short strings).
             * Case-insensitive on the "Bearer" keyword for robustness.
             */
            Pattern.compile(
                    "(Bearer )([A-Za-z0-9._\\-]{8,})",
                    Pattern.CASE_INSENSITIVE
            ),

            /*
             * Pattern 2 — Password key=value or key: value
             *
             * Matches:  "password=secret123"  /  "PASSWORD: topSecret"
             * Group 1:  "password=" or "password: " (key + separator + optional space)
             * The value (group 2 equivalent — \\S+) is the masked portion.
             *
             * \\S+ matches any non-whitespace sequence — passwords can contain
             * special characters such as @, !, $, etc.
             * Case-insensitive on the key name.
             */
            Pattern.compile(
                    "(password[=:]\\s*)\\S+",
                    Pattern.CASE_INSENSITIVE
            ),

            /*
             * Pattern 3 — Client secret key=value or key: value
             *
             * Matches:  "client_secret=abc123def456"
             *           "client.secret=xyz789"
             *           "client_secret: myOAuthSecret"
             *           "CLIENT_SECRET=TopSecret"
             * Group 1:  key + separator + optional whitespace
             *
             * [._] matches both dot and underscore separators used in
             * different naming conventions for OAuth2 client secrets.
             * Case-insensitive match.
             */
            Pattern.compile(
                    "(client[._]secret[=:]\\s*)\\S+",
                    Pattern.CASE_INSENSITIVE
            ),

            /*
             * Pattern 4 — HashiCorp Vault service tokens (hvs.* format)
             *
             * Matches:  "hvs.AAAAAQKsomeVaultToken"
             *           "hvs.CAESILONGTOKENVALUE123"
             * No capturing group — the full token (including hvs. prefix) is
             * replaced with ***MASKED*** because the prefix itself is a strong
             * signal that a secret follows.
             *
             * Minimum 8 alphanumeric characters after the dot prevents masking
             * of the literal string "hvs.short" in documentation or comments.
             * The format hvs.<base62> was introduced in Vault 1.10+.
             */
            Pattern.compile(
                    "hvs\\.[A-Za-z0-9]{8,}"
            )
    );

    /**
     * Applies all secret patterns to the formatted log message and returns the
     * sanitised version.
     *
     * <p>Each pattern is applied sequentially on the result of the previous
     * substitution, so a message containing multiple secret types is fully
     * sanitised in a single pass through all patterns.
     *
     * @param event the logging event; must not be {@code null}
     * @return the sanitised message, or an empty string if the formatted
     *         message was {@code null} or empty
     */
    @Override
    public String convert(ILoggingEvent event) {
        // CONTROL: RN-01 — intercept before the message reaches the appender
        String message = event.getFormattedMessage();

        if (message == null || message.isEmpty()) {
            return "";
        }

        return applyAllMasks(message);
    }

    /**
     * Applies every pattern in {@link #SECRET_PATTERNS} sequentially.
     *
     * <p>Patterns 1–3 have a capturing group (group 1) that preserves the
     * key prefix; only the value is replaced.  Pattern 4 (Vault token) has no
     * group; the full match is replaced.
     *
     * @param message the original formatted message
     * @return the message with all secret values replaced by {@code ***MASKED***}
     */
    private static String applyAllMasks(String message) {
        String result = message;

        for (Pattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            boolean found = false;

            while (matcher.find()) {
                found = true;
                if (matcher.groupCount() >= 1) {
                    // Patterns 1–3: group 1 is the key/prefix to preserve.
                    // Replace everything after group 1 with the mask label.
                    matcher.appendReplacement(sb,
                            Matcher.quoteReplacement(matcher.group(1) + MASK_LABEL));
                } else {
                    // Pattern 4 (Vault token): no group — replace full match.
                    matcher.appendReplacement(sb,
                            Matcher.quoteReplacement(MASK_LABEL));
                }
            }

            if (found) {
                matcher.appendTail(sb);
                result = sb.toString();
            }
        }

        return result;
    }
}
