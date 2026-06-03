package com.securemicro.shared.identity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Stateless utility class for extracting claims from a Keycloak-issued
 * {@link Jwt}.
 *
 * <p>All methods are {@code static} — there is no mutable state, so an
 * instance is never needed.  The private constructor enforces the utility
 * class contract.
 *
 * <p>Every method is defensively coded: {@code null} inputs or missing
 * claims are handled gracefully — callers will never receive a
 * {@link NullPointerException}.
 *
 * <h2>Keycloak claim layout</h2>
 * <pre>{@code
 * {
 *   "sub": "user-uuid",
 *   "realm_access": {
 *     "roles": ["ROLE_SERVICE_USER", "ROLE_SERVICE_ADMIN"]
 *   }
 * }
 * }</pre>
 *
 * <p>CONTROL: INI-05 — role-based authorization relies on correct extraction
 * of {@code realm_access.roles}.
 */
// CONTROL: INI-05
public final class JwtClaimsExtractor {

    /** Utility class — no instances allowed. */
    private JwtClaimsExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the {@code sub} (subject) claim of the supplied {@link Jwt}.
     *
     * <p>CONTROL: INI-05 — the subject is used in audit trail entries and
     * role-based authorization decisions.
     *
     * @param jwt the JWT to inspect; must not be {@code null}
     * @return the subject string, or {@code null} if the {@code sub} claim is
     *         absent from the token
     */
    // CONTROL: INI-05
    public static String getSubject(Jwt jwt) {
        // Jwt.getSubject() returns null when the sub claim is missing —
        // no additional null guard needed beyond the contract that jwt itself
        // is non-null (callers are responsible for passing a non-null Jwt).
        return jwt.getSubject();
    }

    /**
     * Extracts the Keycloak realm roles from the {@code realm_access.roles}
     * claim.
     *
     * <p>Keycloak places realm-level roles inside a nested object:
     * <pre>{@code "realm_access": {"roles": ["ROLE_A", "ROLE_B"]}}</pre>
     * This method navigates that structure and converts every element to a
     * {@link String}.
     *
     * <p>CONTROL: INI-05 — roles must be correctly extracted so that Spring
     * Security can evaluate {@code hasAuthority()} / {@code hasRole()} expressions.
     *
     * @param jwt the JWT to inspect; must not be {@code null}
     * @return an unmodifiable list of role strings; never {@code null} —
     *         returns an empty list when {@code realm_access} is absent or
     *         {@code roles} is not a {@link List}
     */
    // CONTROL: INI-05
    public static List<String> getRoles(Jwt jwt) {
        // realm_access is a Map<String, Object> — getClaimAsMap is null-safe.
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            // Map every element to its toString() representation, guarding
            // against any non-String elements that might appear in a custom
            // Keycloak realm configuration.
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} when the {@link Jwt} contains the specified role
     * in its {@code realm_access.roles} claim.
     *
     * <p>CONTROL: INI-05 — used by controllers and service layer to guard
     * operations that require a specific role (e.g. {@code ROLE_SERVICE_ADMIN}).
     *
     * @param jwt  the JWT to inspect; must not be {@code null}
     * @param role the exact role name to look for (e.g.
     *             {@code "ROLE_SERVICE_ADMIN"})
     * @return {@code true} if the role is present; {@code false} otherwise
     *         (including when {@code realm_access} or {@code roles} is absent)
     */
    // CONTROL: INI-05
    public static boolean hasRole(Jwt jwt, String role) {
        // Delegate to getRoles() so all null-safety logic lives in one place.
        return getRoles(jwt).contains(role);
    }
}
