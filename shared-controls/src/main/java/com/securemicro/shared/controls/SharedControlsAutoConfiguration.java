package com.securemicro.shared.controls;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration entry point for the shared-controls library.
 *
 * <p>This class is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so that any Spring Boot application that has {@code shared-controls} on its
 * classpath will automatically pick up all beans declared in this module
 * without requiring an explicit {@code @Import} in the consuming application.
 *
 * <p>Individual feature configurations (security headers filter, global
 * exception handler, etc.) are declared as {@code @Bean} methods here or in
 * separate {@code @Configuration} classes imported by this one.
 *
 * <p>CONTROL: RN-10  — global exception handling prevents stack trace exposure.
 * <p>CONTROL: INI-04 — uniform HTTP error responses (401/403/4xx/5xx).
 *
 * @see <a href="../../../../../../docs/control-mappings/tmpl-rest-api-controls.md">
 *     Federal Control Mapping</a>
 */
// CONTROL: RN-10
// CONTROL: INI-04
@AutoConfiguration
@Configuration
public class SharedControlsAutoConfiguration {
    // Beans for security headers and global exception handling will be
    // registered in TASK-004 (SecurityHeadersFilter, GlobalExceptionHandler).
    // This class serves as the discoverable auto-configuration root so the
    // Spring Boot auto-configure mechanism can find and load the module.
}
