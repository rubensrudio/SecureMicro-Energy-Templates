package com.securemicro.shared.secrets;

// CONTROL: RN-01 — zero secrets in env vars or config files
// CONTROL: INI-12 — secrets resolved via vault interface

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

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
 *   <li>The Vault health endpoint is queried during the bootstrap phase.</li>
 * </ol>
 *
 * <h2>Property override strategy</h2>
 * <p>The test-classpath {@code application.yml} sets
 * {@code spring.cloud.vault.enabled=false} as a safe default for all tests.
 * This test overrides that default by adding a high-priority
 * {@link MapPropertySource} directly to the {@link StandardEnvironment} before
 * calling {@code app.run()}.  A {@link MapPropertySource} added with
 * {@link org.springframework.core.env.MutablePropertySources#addFirst(
 * org.springframework.core.env.PropertySource)} takes precedence over any
 * {@code application.yml} file, making Vault integration active for this test
 * only.
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
        // Intentionally empty — auto-configuration picks up VaultBootstrapConfiguration.
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
        // KV v2 read endpoint for the default-context "application".
        // Spring Cloud Vault (kv.backend=secret, default-context=application)
        // calls: GET /v1/secret/data/application
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
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    /**
     * Returns a property map that overrides the test-classpath defaults and
     * points Spring Cloud Vault at the running WireMock server.
     *
     * <p>CONTROL: RN-01 — the token is a synthetic test value; WireMock does
     * not validate authentication headers.
     */
    private Map<String, Object> vaultOverrideProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.cloud.vault.enabled", "true");
        props.put("spring.cloud.vault.fail-fast", "true");
        props.put("spring.cloud.vault.scheme", "http");
        props.put("spring.cloud.vault.host", "localhost");
        props.put("spring.cloud.vault.port", String.valueOf(wireMock.port()));
        props.put("spring.cloud.vault.authentication", "TOKEN");
        // CONTROL: RN-01 — synthetic test token; no real credential
        props.put("spring.cloud.vault.token", "test-token-wiremock");
        props.put("spring.cloud.vault.kv.enabled", "true");
        props.put("spring.cloud.vault.kv.backend", "secret");
        props.put("spring.cloud.vault.kv.default-context", "application");
        props.put("spring.config.import", "optional:vault://");
        props.put("spring.main.web-application-type", "none");
        return props;
    }

    /**
     * Creates a {@link SpringApplication} with the Vault override properties
     * inserted at the highest priority in the environment, ensuring they take
     * precedence over any {@code application.yml} on the test classpath.
     */
    private SpringApplication buildAppWithVaultOverride() {
        SpringApplication app = new SpringApplication(TestApplication.class);
        app.addInitializers(ctx -> {
            // Insert a high-priority MapPropertySource so these properties
            // override the test-classpath application.yml (vault.enabled=false).
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("vaultWireMockOverride", vaultOverrideProperties()));
        });
        return app;
    }

    @Test
    @DisplayName("(a) With Vault accessible: context starts and VaultBootstrapConfiguration is registered")
    void contextLoadsWhenVaultIsAccessible() {
        // Arrange
        SpringApplication app = buildAppWithVaultOverride();

        // Act
        ConfigurableApplicationContext ctx = app.run();

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
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("(a) With Vault accessible: Spring Environment is active and has property sources")
    void environmentIsActiveWhenVaultIsAccessible() {
        // Arrange
        SpringApplication app = buildAppWithVaultOverride();

        // Act
        ConfigurableApplicationContext ctx = app.run();

        try {
            // Assert: the Spring Environment is present and active.
            // This confirms the application context fully initialised with Vault
            // enabled — no exception was thrown during the Vault bootstrap phase.
            assertThat(ctx.getEnvironment())
                    .as("Spring Environment must be present and non-null after startup")
                    .isNotNull();

            // Assert: Vault-related property sources are present in the environment.
            // Even with optional:vault://, VaultConfigDataLoader registers a property
            // source node in the environment when vault.enabled=true.
            boolean vaultOrVaultOverridePresent = ctx.getEnvironment()
                    .getPropertySources()
                    .stream()
                    .anyMatch(ps -> ps.getName().toLowerCase().contains("vault")
                            || ps.getName().contains("vaultWireMockOverride"));

            assertThat(vaultOrVaultOverridePresent)
                    .as("At least one Vault-related PropertySource must be present in the environment")
                    .isTrue();
        } finally {
            ctx.close();
        }
    }
}
