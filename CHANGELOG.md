# Changelog

All notable changes to this project are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — 2026-06-03

First public reference release. Phase 1 scope: REST API template + four shared
security libraries + scaffold CLI, all aligned to EO 14028 / NIST SP 800-53
Rev 5 / CISA Secure by Design.

### Added
- **tmpl-rest-api** — Spring Boot 3.3.11 WebFlux REST template with OIDC auth,
  Vault-backed secrets, JSON audit logging, Micrometer metrics, OTel tracing,
  CycloneDX SBOM (166 components) and a 568-line STRIDE threat model.
- **shared-controls** — `SecurityHeadersFilter` (HSTS, CSP, X-Frame-Options) +
  `GlobalExceptionHandler` (no stack-trace leakage).
- **shared-observability** — structured JSON logging, Micrometer/Prometheus,
  OpenTelemetry SDK, `AuditTrailService` (`event-type=AUDIT`).
- **shared-identity** — Keycloak JWT validation (OAuth2 resource server) + RBAC.
- **shared-secrets** — HashiCorp Vault KV v2 via Spring Cloud Vault, fail-fast
  startup, secret masking in logs.
- **sme-cli** — Python CLI to scaffold services from templates (62 tests).
- **reference-deploy** — Docker Compose: Keycloak + Vault (dev) + service + Jaeger.
- **docs/** — control mappings, threat models, quickstart, architecture diagrams,
  benchmarks, screenshots guide, demo, external-validation tracker.
- **LICENSE** (Apache-2.0) + **NOTICE**.

### Validated
- E2E validation (TASK-029): **9 PASS / 3 NOT_TESTED** (NOT_TESTED blocked only
  by absence of Docker in the validation env). 31 Java tests, 62 CLI tests, 0
  real secrets in repo, 100% control↔code traceability across 21 controls.

### Known limitations
- Quickstart timing (AC-5), Prometheus scrape (AC-7), Jaeger trace (AC-8) not
  executed live — pending a Docker-enabled run. See
  [`docs/validation/`](docs/validation/).
- tmpl-batch-job and tmpl-event-driven are Phase 2 (planned, directories reserved).
- External attestations (Scorecard, SLSA, signed SBOM) not yet obtained — see
  [`docs/validation/EXTERNAL-VALIDATION.md`](docs/validation/EXTERNAL-VALIDATION.md).

[0.1.0]: https://github.com/rubensrudio/SecureMicro-Energy-Templates/releases/tag/v0.1.0
