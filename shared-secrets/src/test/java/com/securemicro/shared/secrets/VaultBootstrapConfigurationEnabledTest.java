package com.securemicro.shared.secrets;

// CONTROL: RN-01 — zero secrets in env vars or config files
// CONTROL: INI-12 — secrets resolved via vault interface

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — criterion (a): Vault is accessible (WireMock simulates the
 * Vault KV v2 REST API).
 *
 * <p>Verifies that when Spring Cloud Vault can reach the Vault server:
 * <ol>
 *   <li>The application context starts successfully.</li>
 *   <li>{@link VaultBootstrapConfiguration} is registered as a bean.</li>
 *   <li>The {@code test.secret.value} property is resolved from Vault KV,
 *       proving control INI-12 ("secrets resolved via Vault at runtime").</li>
 * </ol>
 *
 * <h2>Property override strategy</h2>
 * <p>The test-classpath {@code application.yml} sets
 * {@code spring.cloud.vault.enabled=false} as a safe default for all tests.
 * This test overrides that default by passing CLI arguments to
 * {@link SpringApplication#run(String...)}. Command-line arguments have the
 * highest property-source priority in Spring Boot AND are processed BEFORE the
 * Config Data phase — which is exactly when {@code spring.config.import=vault://}
 * is resolved and the KV v2 fetch happens.  Using {@code addInitializers} or
 * {@code setDefaultProperties} would inject properties AFTER Config Data has
 * already run, so the Vault fetch would never see them.
 *
 * <h2>WireMock stubs</h2>
 * <p>Spring Cloud Vault 4.x uses two parallel mechanisms:
 * <ol>
 *   <li>{@code VaultConfigDataLoader} (triggered by {@code spring.config.import=vault://})
 *       calls the KV v2 endpoint: {@code GET /v1/secret/data/application}.</li>
 *   <li>{@code LeaseAwareVaultPropertySource} (triggered by
 *       {@code spring.cloud.vault.kv.enabled=true}) calls the KV v1-style
 *       endpoint: {@code GET /v1/secret/application}.  Both stubs are required
 *       because both mechanisms are active when all vault properties are set.</li>
 * </ol>
 *
 * <p>CONTROL: RN-01 — no literal secret values in source; WireMock returns
 * synthetic values for test purposes only.
 * <p>CONTROL: INI-12 — property source populated from Vault mock responses.
 */
// CONTROL: RN-01
// CONTROL: INI-12
class VaultBootstrapConfigurationEnabledTest {

    @SpringBootApplication
    static class TestApplication {
        // Auto-configuration picks up VaultBootstrapConfiguration.

        /**
         * Injects the secret value resolved from Vault (WireMock stub).
         *
         * <p>The default {@code "NOT_RESOLVED"} sentinel is intentional: if Spring
         * Cloud Vault silently skips the KV fetch (e.g. because {@code optional:}
         * is set), the field retains the sentinel and the assertion in
         * {@link #contextLoadsWhenVaultIsAccessible()} will fail the test,
         * making Vault resolution mandatory and observable.
         *
         * <p>CONTROL: INI-12 — proves secrets are resolved via Vault at runtime.
         * <p>CONTROL: RN-01 — {@code "resolved-from-vault"} is a synthetic test value;
         * no real credential appears in source.
         */
        @Component
        static class SecretProbe {
            @Value("${test.secret.value:NOT_RESOLVED}")
            String secretValue;
        }
    }

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        // ----------------------------------------------------------------
        // Vault health endpoint — Spring Cloud Vault calls GET /v1/sys/health
        // to verify reachability before loading secrets.
        // ----------------------------------------------------------------
        wireMock.stubFor(get(urlEqualTo("/v1/sys/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"initialized\":true,\"sealed\":false,\"standby\":false}")));

        // ----------------------------------------------------------------
        // KV v2 endpoint — used by VaultConfigDataLoader when
        // spring.config.import=vault:// is active.
        // Path: GET /v1/secret/data/application
        //
        // CONTROL: RN-01 — "resolved-from-vault" is a synthetic test value.
        // ----------------------------------------------------------------
        wireMock.stubFor(get(urlEqualTo("/v1/secret/data/application"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": {
                                    "data": {
                                      "test.secret.value": "resolved-from-vault"
                                    },
                                    "metadata": { "version": 1 }
                                  }
                                }
                                """)));

        // ----------------------------------------------------------------
        // KV v1-style endpoint — used by LeaseAwareVaultPropertySource when
        // spring.cloud.vault.kv.enabled=true.
        // Path: GET /v1/secret/application
        //
        // CONTROL: RN-01 — "resolved-from-vault" is a synthetic test value.
        // ----------------------------------------------------------------
        wireMock.stubFor(get(urlEqualTo("/v1/secret/application"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": {
                                    "test.secret.value": "resolved-from-vault"
                                  },
                                  "lease_duration": 2764800,
                                  "renewable": false
                                }
                                """)));

        // ----------------------------------------------------------------
        // KV v1-style endpoint for the application-name context.
        // LeaseAwareVaultPropertySource also tries GET /v1/secret/{app-name}.
        // Return 404 to signal "no specific app secrets" — not an error.
        // ----------------------------------------------------------------
        wireMock.stubFor(get(urlEqualTo("/v1/secret/shared-secrets-test"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[]}")));
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    /**
     * Returns the Vault override properties as command-line args for
     * {@link SpringApplication#run(String...)}.
     *
     * <p>Command-line arguments have the highest property-source priority in
     * Spring Boot and — critically — are parsed BEFORE the Config Data phase
     * (which is when {@code spring.config.import=vault://} is resolved and the
     * KV v2 fetch happens). This is the only strategy that ensures the Vault
     * properties are visible to the Config Data loader.
     *
     * <p>CONTROL: RN-01 — the token is a synthetic test value; WireMock does
     * not validate authentication headers.
     */
    private String[] vaultOverrideArgs() {
        return new String[]{
                "--spring.cloud.vault.enabled=true",
                "--spring.cloud.vault.fail-fast=true",
                "--spring.cloud.vault.scheme=http",
                "--spring.cloud.vault.host=localhost",
                "--spring.cloud.vault.port=" + wireMock.port(),
                "--spring.cloud.vault.authentication=TOKEN",
                // CONTROL: RN-01 — synthetic test token; no real credential
                "--spring.cloud.vault.token=test-token-wiremock",
                "--spring.cloud.vault.kv.enabled=true",
                "--spring.cloud.vault.kv.backend=secret",
                "--spring.cloud.vault.kv.default-context=application",
                // Use vault:// (non-optional) — mirrors production application-vault.yml.
                // With optional:vault://, a KV fetch failure would be silenced and the
                // test would not constitute evidence of control INI-12.
                // CONTROL: INI-12 — mandatory import ensures secrets MUST be resolved.
                "--spring.config.import=vault://",
                "--spring.main.web-application-type=none"
        };
    }

    @Test
    @DisplayName("(a) With Vault accessible: context starts, VaultBootstrapConfiguration registered, secret resolved")
    void contextLoadsWhenVaultIsAccessible() {
        // Arrange: build the application and pass Vault config as CLI args so they
        // are available during the Config Data bootstrap phase (highest priority).
        SpringApplication app = new SpringApplication(TestApplication.class);

        // Act
        ConfigurableApplicationContext ctx = app.run(vaultOverrideArgs());

        try {
            // Assert: context is fully initialised
            assertThat(ctx.isActive())
                    .as("Application context must be active after successful Vault bootstrap")
                    .isTrue();

            // Assert: VaultBootstrapConfiguration bean is present.
            // @AutoConfiguration beans use the FQN as bean name; getBeansOfType() is
            // name-agnostic and works regardless of the registration strategy.
            assertThat(ctx.getBeansOfType(VaultBootstrapConfiguration.class))
                    .as("VaultBootstrapConfiguration must be registered when vault.enabled=true")
                    .isNotEmpty();

            // Assert: the secret value was actually resolved from Vault.
            // The SecretProbe bean defaults to "NOT_RESOLVED" sentinel if the KV
            // fetch was skipped or silenced — proving that vault:// (non-optional)
            // forces a successful resolution before the context becomes active.
            // CONTROL: INI-12 — executable evidence that secrets are resolved via Vault.
            TestApplication.SecretProbe probe = ctx.getBean(TestApplication.SecretProbe.class);
            assertThat(probe.secretValue)
                    .as("test.secret.value must be resolved from Vault WireMock stub "
                            + "— 'NOT_RESOLVED' means the KV fetch was skipped (optional: mode leak)")
                    .isEqualTo("resolved-from-vault");
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("(a) With Vault accessible: Spring Environment is active and has Vault property sources")
    void environmentIsActiveWhenVaultIsAccessible() {
        // Arrange: use CLI args so they are processed before the Config Data phase.
        SpringApplication app = new SpringApplication(TestApplication.class);

        // Act
        ConfigurableApplicationContext ctx = app.run(vaultOverrideArgs());

        try {
            // Assert: the Spring Environment is present and active.
            // This confirms the application context fully initialised with Vault
            // enabled — no exception was thrown during the Vault bootstrap phase.
            assertThat(ctx.getEnvironment())
                    .as("Spring Environment must be present and non-null after startup")
                    .isNotNull();

            // Assert: the secret property is present in the environment.
            // This verifies that VaultConfigDataLoader / LeaseAwareVaultPropertySource
            // contributed at least one Vault-sourced property into the environment.
            // Using getProperty() is name-agnostic and works regardless of how
            // Spring Cloud Vault names its PropertySource entries.
            // CONTROL: INI-12 — Vault-sourced properties are available in the environment.
            String resolvedValue = ctx.getEnvironment().getProperty("test.secret.value");
            assertThat(resolvedValue)
                    .as("test.secret.value must be present in the environment "
                            + "— confirms Vault PropertySource contributed properties")
                    .isNotNull()
                    .isEqualTo("resolved-from-vault");
        } finally {
            ctx.close();
        }
    }
}
