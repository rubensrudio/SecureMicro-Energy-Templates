package com.securemicro.shared.controls;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration entry point for the shared-controls library.
 *
 * <p>This class is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so that any Spring Boot application that has {@code shared-controls} on its
 * classpath will automatically pick up all beans declared in this module
 * without requiring an explicit {@code @Import} in the consuming application.
 *
 * <p>{@code @AutoConfiguration} is already meta-annotated with
 * {@code @Configuration} — adding an explicit {@code @Configuration} annotation
 * would be redundant and is therefore omitted (resolved in TASK-004 review).
 *
 * <p>Each {@code @Bean} method is guarded by {@code @ConditionalOnMissingBean}
 * so that consuming services (e.g. {@code tmpl-rest-api} in TASK-013) can
 * override any bean by declaring their own — preventing duplicate-bean conflicts
 * at context startup.
 *
 * <p>CONTROL: RN-10  — global exception handling prevents stack trace exposure.
 * <p>CONTROL: INI-04 — uniform HTTP error responses (401/403/4xx/5xx).
 *
 * @see SecurityHeadersFilter
 * @see GlobalExceptionHandler
 * @see <a href="../../../../../../docs/control-mappings/tmpl-rest-api-controls.md">
 *     Federal Control Mapping</a>
 */
// CONTROL: RN-10
// CONTROL: INI-04
@AutoConfiguration
public class SharedControlsAutoConfiguration {

    /**
     * Registers {@link SecurityHeadersFilter} as a Spring-managed bean.
     *
     * <p>The {@code @ConditionalOnMissingBean} guard ensures that if a
     * consuming application declares its own {@code SecurityHeadersFilter}
     * (e.g. to add extra headers), this auto-configured instance is skipped,
     * preventing a duplicate-bean error at context startup (safe for TASK-013).
     *
     * @return configured {@link SecurityHeadersFilter} instance
     */
    @Bean
    @ConditionalOnMissingBean(SecurityHeadersFilter.class)
    public SecurityHeadersFilter securityHeadersFilter() {
        // CONTROL: RN-10 — inject mandatory security headers on every response
        return new SecurityHeadersFilter();
    }

    /**
     * Registers {@link GlobalExceptionHandler} as a Spring-managed bean.
     *
     * <p>The {@code @ConditionalOnMissingBean} guard allows consuming services
     * to provide a more specific exception handler without conflicting with
     * this library-level default.
     *
     * @return configured {@link GlobalExceptionHandler} instance
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        // CONTROL: RN-10 — return generic error responses, never stack traces
        // CONTROL: INI-04 — uniform error response format across all services
        return new GlobalExceptionHandler();
    }
}
