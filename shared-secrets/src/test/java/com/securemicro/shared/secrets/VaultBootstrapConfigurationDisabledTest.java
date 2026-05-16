package com.securemicro.shared.secrets;

// CONTROL: RN-01 — zero secrets in env vars or config files
// CONTROL: INI-12 — secrets resolved via vault interface

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: Vault integration disabled via
 * {@code spring.cloud.vault.enabled=false}.
 *
 * <p>Verifies that when Vault is disabled the application context starts
 * successfully without any Vault connectivity, and that the
 * {@code VaultBootstrapConfiguration} bean is NOT registered (because
 * {@code @ConditionalOnProperty} evaluates to false).
 *
 * <p>This path is used in local development without a live Vault instance and
 * in unit/integration tests that do not mock the Vault API.
 *
 * <p>CONTROL: RN-01 — conditional disablement does not expose any secret value.
 * <p>CONTROL: INI-12 — when enabled, secrets come from Vault; this test
 * validates the disabled path only.
 */
// CONTROL: RN-01
// CONTROL: INI-12
class VaultBootstrapConfigurationDisabledTest {

    @SpringBootApplication
    static class TestApplication {
        // Minimal application context anchor — auto-configuration handles the rest.
    }

    @Test
    @DisplayName("Context starts normally when spring.cloud.vault.enabled=false (no Vault required)")
    void contextStartsWithVaultDisabled() {
        // Arrange
        SpringApplication app = new SpringApplication(TestApplication.class);

        // Act: start context with Vault disabled — no Vault server needed
        ConfigurableApplicationContext ctx = app.run(
                "--spring.main.web-application-type=none",
                "--spring.cloud.vault.enabled=false"
        );

        try {
            // Assert: context is live
            assertThat(ctx).isNotNull();
            assertThat(ctx.isActive())
                    .as("Application context should be active after startup")
                    .isTrue();

            // Assert: VaultBootstrapConfiguration is NOT loaded when disabled.
            // @AutoConfiguration beans use the fully-qualified name; we use getBeansOfType()
            // which is name-agnostic and works regardless of registration strategy.
            assertThat(ctx.getBeansOfType(VaultBootstrapConfiguration.class))
                    .as("VaultBootstrapConfiguration bean must NOT be present when vault is disabled")
                    .isEmpty();
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("spring.cloud.vault.enabled defaults to true — @ConditionalOnProperty matchIfMissing=true")
    void conditionalOnPropertyMatchIfMissingIsTrue() {
        // Verify that the annotation metadata on VaultBootstrapConfiguration
        // declares matchIfMissing = true, meaning the bean is active by default.
        //
        // We inspect the annotation directly rather than starting a full context
        // with a live Vault, keeping this test fast and self-contained.
        var annotation = VaultBootstrapConfiguration.class
                .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        assertThat(annotation)
                .as("VaultBootstrapConfiguration must carry @ConditionalOnProperty")
                .isNotNull();

        assertThat(annotation.name())
                .as("Property name must be 'spring.cloud.vault.enabled'")
                .contains("spring.cloud.vault.enabled");

        assertThat(annotation.matchIfMissing())
                .as("matchIfMissing must be true so Vault is enabled by default")
                .isTrue();
    }
}
