package com.securemicro.shared.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link JwtClaimsExtractor}.
 *
 * <p>All tests use {@link Jwt#withTokenValue(String)} to construct minimal
 * {@link Jwt} instances — no live Keycloak or WireMock required.
 *
 * <p>Scenarios verified:
 * <ol>
 *   <li>{@code getSubject()} returns the correct {@code sub} claim.</li>
 *   <li>{@code getSubject()} on a JWT without a {@code sub} returns {@code null}
 *       — no NPE.</li>
 *   <li>{@code getRoles()} returns the list when {@code realm_access.roles} is
 *       present.</li>
 *   <li>{@code getRoles()} returns an empty list when {@code realm_access} is
 *       absent.</li>
 *   <li>{@code hasRole()} returns {@code true} for a present role and
 *       {@code false} for an absent one.</li>
 *   <li>{@code hasRole()} with a JWT that carries no roles returns {@code false}
 *       — no NPE.</li>
 * </ol>
 *
 * <p>CONTROL: INI-05
 */
// CONTROL: INI-05
class JwtClaimsExtractorTest {

    // ── Helper factory ──────────────────────────────────────────────────────

    /**
     * Builds a minimal {@link Jwt} with the supplied claims.
     *
     * <p>The token value, issued-at and expiry are synthetic — only the claims
     * map is exercised by {@link JwtClaimsExtractor}.
     */
    private static Jwt buildJwt(String subject, Map<String, Object> realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (subject != null) {
            builder.subject(subject);
        }
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }

    // ── getSubject() ────────────────────────────────────────────────────────

    /**
     * When the JWT carries a {@code sub} claim, {@code getSubject()} must
     * return that exact value.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("getSubject() returns the sub claim from a valid JWT")
    void getSubject_returnsSubClaim_whenPresent() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt("user-abc-123", null);

        String subject = JwtClaimsExtractor.getSubject(jwt);

        assertThat(subject).isEqualTo("user-abc-123");
    }

    /**
     * When the JWT has no {@code sub} claim, {@code getSubject()} must return
     * {@code null} without throwing a {@link NullPointerException}.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("getSubject() returns null (no NPE) when sub is absent")
    void getSubject_returnsNull_whenSubAbsent() {
        // CONTROL: INI-05
        // Build a JWT explicitly without a subject claim.
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of("ROLE_SERVICE_USER")))
                .build();

        assertThatNoException()
                .as("getSubject() must not throw NPE when sub is absent")
                .isThrownBy(() -> JwtClaimsExtractor.getSubject(jwt));

        assertThat(JwtClaimsExtractor.getSubject(jwt))
                .as("getSubject() must return null when sub claim is absent")
                .isNull();
    }

    // ── getRoles() ──────────────────────────────────────────────────────────

    /**
     * When {@code realm_access.roles} is present, {@code getRoles()} must
     * return a list containing all the role strings from that claim.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("getRoles() returns the list when realm_access.roles is present")
    void getRoles_returnsRoleList_whenRealmAccessPresent() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt(
                "service-user",
                Map.of("roles", List.of("ROLE_SERVICE_ADMIN", "ROLE_SERVICE_USER")));

        List<String> roles = JwtClaimsExtractor.getRoles(jwt);

        assertThat(roles)
                .as("getRoles() must return all roles from realm_access.roles")
                .containsExactlyInAnyOrder("ROLE_SERVICE_ADMIN", "ROLE_SERVICE_USER");
    }

    /**
     * When the JWT has no {@code realm_access} claim at all, {@code getRoles()}
     * must return an empty list — no NPE.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("getRoles() returns empty list when realm_access is absent")
    void getRoles_returnsEmptyList_whenRealmAccessAbsent() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt("user-no-roles", null); // no realm_access claim

        assertThatNoException()
                .as("getRoles() must not throw NPE when realm_access is absent")
                .isThrownBy(() -> JwtClaimsExtractor.getRoles(jwt));

        List<String> roles = JwtClaimsExtractor.getRoles(jwt);
        assertThat(roles)
                .as("getRoles() must return an empty list when realm_access is absent")
                .isEmpty();
    }

    // ── hasRole() ───────────────────────────────────────────────────────────

    /**
     * When the JWT contains the queried role in {@code realm_access.roles},
     * {@code hasRole()} must return {@code true}.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("hasRole() returns true when the role is present")
    void hasRole_returnsTrue_whenRolePresent() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt(
                "admin-user",
                Map.of("roles", List.of("ROLE_SERVICE_ADMIN")));

        assertThat(JwtClaimsExtractor.hasRole(jwt, "ROLE_SERVICE_ADMIN"))
                .as("hasRole() must return true when ROLE_SERVICE_ADMIN is present")
                .isTrue();
    }

    /**
     * When the JWT does NOT contain the queried role in {@code realm_access.roles},
     * {@code hasRole()} must return {@code false}.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("hasRole() returns false when the role is absent from realm_access")
    void hasRole_returnsFalse_whenRoleAbsent() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt(
                "regular-user",
                Map.of("roles", List.of("ROLE_SERVICE_USER")));

        assertThat(JwtClaimsExtractor.hasRole(jwt, "ROLE_SERVICE_ADMIN"))
                .as("hasRole() must return false when ROLE_SERVICE_ADMIN is not present")
                .isFalse();
    }

    /**
     * When the JWT has no {@code realm_access} claim, {@code hasRole()} must
     * return {@code false} without throwing a {@link NullPointerException}.
     *
     * <p>CONTROL: INI-05
     */
    @Test
    @DisplayName("hasRole() returns false (no NPE) when JWT has no roles")
    void hasRole_returnsFalse_whenNoRolesInJwt() {
        // CONTROL: INI-05
        Jwt jwt = buildJwt("user-without-roles", null); // no realm_access

        assertThatNoException()
                .as("hasRole() must not throw NPE when realm_access is absent")
                .isThrownBy(() -> JwtClaimsExtractor.hasRole(jwt, "ROLE_SERVICE_ADMIN"));

        assertThat(JwtClaimsExtractor.hasRole(jwt, "ROLE_SERVICE_ADMIN"))
                .as("hasRole() must return false when no roles are present in the JWT")
                .isFalse();
    }
}
