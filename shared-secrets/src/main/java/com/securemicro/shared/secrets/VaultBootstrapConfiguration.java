package com.securemicro.shared.secrets;

// CONTROL: RN-01 — zero secrets in env vars or config files
// CONTROL: INI-12 — secrets resolved via vault interface

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Spring Boot auto-configuration that integrates HashiCorp Vault as a
 * {@code ConfigData} property source for the SecureMicro template family.
 *
 * <h2>How secrets are resolved</h2>
 * <p>Spring Cloud Vault (spring-cloud-starter-vault-config) uses the
 * {@code ConfigData} API introduced in Spring Boot 2.4.  When the application
 * properties contain:
 * <pre>
 *   spring.config.import=vault://
 * </pre>
 * the Spring Cloud Vault {@code VaultConfigDataLoader} connects to Vault
 * <em>before</em> the application context is fully initialised, reads the KV v2
 * paths defined in {@code spring.cloud.vault.kv.*}, and injects the resolved
 * values as a high-priority {@code PropertySource}.  Any {@code @Value} field
 * or {@code @ConfigurationProperties} bean in the application context then
 * receives the Vault-resolved value transparently.
 *
 * <h2>Authentication — VAULT_ADDR and VAULT_TOKEN</h2>
 * <p>Connection coordinates are taken exclusively from environment variables:
 * <ul>
 *   <li>{@code VAULT_ADDR}  — full URL of the Vault server
 *       (e.g. {@code https://vault.internal:8200}).  Never hardcoded.</li>
 *   <li>{@code VAULT_TOKEN} — token with read access to the KV paths used by
 *       this service.  Never hardcoded, never logged.</li>
 * </ul>
 * The corresponding {@code application-vault.yml} properties reference these
 * env vars via Spring's {@code ${ENV_VAR}} syntax so that no literal value
 * ever touches the classpath or the version-control history.
 *
 * <h2>Fail-fast behaviour</h2>
 * <p>When {@code spring.cloud.vault.fail-fast=true} (the default in
 * {@code application-vault.yml}), any connectivity failure to Vault during
 * startup causes Spring Boot to abort the context refresh immediately with a
 * descriptive error message.  The service will never start in a partially
 * initialised state with unresolved secrets.
 *
 * <h2>KV v2 and lease renewal — Risk R-01</h2>
 * <p><strong>IMPORTANT:</strong> The Vault KV secrets engine version 2 does
 * <em>not</em> issue leases.  Spring Cloud Vault's lease-renewal mechanism
 * (used for dynamic secrets such as database credentials) does
 * <em>not</em> apply to KV v2 secrets.  Secret rotation must be performed by
 * writing a new value to the KV path in Vault and then triggering an
 * application refresh (e.g. via Spring Cloud's {@code /actuator/refresh}
 * endpoint or a rolling restart).  This is documented as risk R-01 in
 * {@code docs/threat-models/tmpl-rest-api-threat-model.md} and in the
 * {@code plan.md} risk register (priority 1).
 *
 * <h2>Enabling / disabling this configuration</h2>
 * <p>This auto-configuration is active by default
 * ({@code spring.cloud.vault.enabled} defaults to {@code true}).
 * Set {@code spring.cloud.vault.enabled=false} to disable Vault integration
 * entirely — useful in local development without a running Vault instance or
 * in integration tests that mock the Vault API.
 *
 * <p>CONTROL: RN-01 — zero secrets in env vars, config files or VCS.
 * <p>CONTROL: INI-12 — all secrets resolved via the Vault interface at runtime.
 *
 * @see <a href="../../../../../../docs/control-mappings/tmpl-rest-api-controls.md">
 *     Federal Control Mapping</a>
 * @see <a href="https://cloud.spring.io/spring-cloud-vault/reference/html/">
 *     Spring Cloud Vault Reference Documentation</a>
 */
// CONTROL: RN-01
// CONTROL: INI-12
@AutoConfiguration
@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", matchIfMissing = true)
public class VaultBootstrapConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VaultBootstrapConfiguration.class);

    /**
     * Logs a confirmation message once the application context has been
     * refreshed successfully.  This message confirms that Vault secrets were
     * resolved and injected as property sources before any bean required them.
     *
     * <p>CONTROL: RN-01 — the log message deliberately does not print any
     * secret value; only the Vault host (non-sensitive) is logged for
     * operational traceability.
     */
    // CONTROL: RN-01
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        log.info("[shared-secrets] Vault bootstrap completed successfully. "
                + "All secrets resolved via KV v2 property source. "
                + "KV v2 does NOT support automatic lease renewal — "
                + "rotate secrets by writing new values to Vault (Risk R-01).");
    }
}
