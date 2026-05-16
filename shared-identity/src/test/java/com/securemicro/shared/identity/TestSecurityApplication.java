package com.securemicro.shared.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Minimal Spring Boot application used exclusively in tests.
 *
 * <p>Provides stub HTTP endpoints that allow
 * {@link IdentitySecurityConfigurationTest} to exercise the security
 * filter chain without depending on the real {@code tmpl-rest-api}
 * application code.
 *
 * <p>This class is intentionally placed under {@code src/test/} and is
 * NEVER part of the production JAR.
 */
@SpringBootApplication
@EnableReactiveMethodSecurity
public class TestSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestSecurityApplication.class, args);
    }

    // ── Stub controllers ────────────────────────────────────────────────────

    /**
     * Simulates a secured API endpoint accessible to users with
     * {@code ROLE_SERVICE_USER}.
     */
    @RestController
    @RequestMapping("/api/v1")
    static class StubApiController {

        /** Stub for GET /api/v1/hello — requires ROLE_SERVICE_USER. */
        @GetMapping("/hello")
        @PreAuthorize("hasAuthority('ROLE_SERVICE_USER')")
        public Mono<Map<String, String>> hello() {
            return Mono.just(Map.of("message", "hello"));
        }

        /** Stub for POST /api/v1/admin — requires ROLE_SERVICE_ADMIN. */
        @PostMapping("/admin")
        @PreAuthorize("hasAuthority('ROLE_SERVICE_ADMIN')")
        public Mono<Map<String, String>> admin() {
            return Mono.just(Map.of("message", "admin ok"));
        }
    }

    /**
     * Simulates the Spring Boot Actuator health and prometheus endpoints.
     * These must be accessible without authentication (CONTROL: INI-08).
     */
    @RestController
    @RequestMapping("/actuator")
    static class StubActuatorController {

        @GetMapping("/health")
        public Mono<Map<String, String>> health() {
            return Mono.just(Map.of("status", "UP"));
        }

        @GetMapping("/health/liveness")
        public Mono<Map<String, String>> liveness() {
            return Mono.just(Map.of("status", "UP"));
        }

        @GetMapping("/health/readiness")
        public Mono<Map<String, String>> readiness() {
            return Mono.just(Map.of("status", "UP"));
        }

        @GetMapping("/prometheus")
        public Mono<String> prometheus() {
            return Mono.just("# HELP jvm_memory_used_bytes\njvm_memory_used_bytes 0\n");
        }
    }
}
