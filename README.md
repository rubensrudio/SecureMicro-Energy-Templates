# SecureMicro-Energy-Templates

Production-grade microservice templates for the US energy sector, aligned to
**EO 14028** (Improving the Nation's Cybersecurity), **NIST SP 800-53 Rev 5**,
and **CISA Secure by Design Guidance**.

Each template ships with authentication (Keycloak OIDC), secrets management
(HashiCorp Vault), structured JSON logging, Micrometer metrics, OpenTelemetry
tracing, CycloneDX SBOM generation, and a full STRIDE threat model — so
engineering teams get a compliant, auditable starting point without a dedicated
platform-engineering or AppSec team.

---

## Component Status

<!-- AC INI-02: every component must declare its status explicitly -->

| Componente | Tipo | Status | Descricao |
|---|---|---|---|
| tmpl-rest-api | Template | Phase 1 — implementado | Spring Boot 3 WebFlux REST API com seguranca completa (OIDC, Vault, OTel, SBOM) |
| shared-controls | Biblioteca | Phase 1 — implementado | Security headers (HSTS, CSP, X-Frame-Options) e GlobalExceptionHandler sem vazamento de stack trace |
| shared-observability | Biblioteca | Phase 1 — implementado | Logging JSON estruturado (logstash-logback-encoder), Micrometer/Prometheus, OpenTelemetry SDK |
| shared-identity | Biblioteca | Phase 1 — implementado | Keycloak JWT validation (resource server OAuth2), extracao de claims e RBAC |
| shared-secrets | Biblioteca | Phase 1 — implementado | HashiCorp Vault KV v2 via Spring Cloud Vault; mascaramento de secrets em logs |
| sme-cli | CLI | Phase 1 — implementado | Python CLI para scaffold de novos microservicos a partir dos templates |
| reference-deploy | Infra | Phase 1 — planejado | Docker Compose reference: Keycloak + Vault (dev) + servico + Jaeger (TASK-022) |
| tmpl-batch-job | Template | Phase 2 — planejado | Spring Batch template com controles federais |
| tmpl-event-driven | Template | Phase 2 — planejado | Template orientado a eventos (Kafka/RabbitMQ) com controles federais |

---

## Repository Structure

```
SecureMicro-Energy-Templates/
├── pom.xml                  # Root BOM — Multi-Module Maven
├── shared-controls/         # SecurityHeadersFilter + GlobalExceptionHandler
├── shared-observability/    # JSON logging, Micrometer/Prometheus, OTel tracing
├── shared-identity/         # Keycloak JWT validation, RBAC, WebFlux security
├── shared-secrets/          # HashiCorp Vault KV v2 via Spring Cloud Vault
├── tmpl-rest-api/           # Template REST API completo (Phase 1 — implementado)
├── tmpl-batch-job/          # Template Batch Job (Phase 2 — planejado)
├── tmpl-event-driven/       # Template Event-Driven (Phase 2 — planejado)
├── reference-deploy/        # Docker Compose reference (Phase 1 — planejado)
├── sme-cli/                 # Python CLI para scaffold (Phase 1 — implementado)
└── docs/                    # Threat models, control mappings, quickstart guides
    ├── control-mappings/
    ├── threat-models/
    └── quickstart/
```

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | Build and run the Spring Boot templates |
| Maven | 3.9+ | Multi-module build (`mvn clean install`) |
| Python | 3.11+ | Run `sme-cli` scaffold tool |
| Docker | 24+ | Run `reference-deploy` Docker Compose stack |

---

## Quickstart

### 1. Clone and build all modules

```bash
git clone <repo-url>
cd SecureMicro-Energy-Templates
mvn clean install -DskipTests
```

### 2. Scaffold a new service with sme-cli

```bash
cd sme-cli
pip install -e .
sme new tmpl-rest-api meu-servico
```

This creates `./meu-servico/` with all Java packages renamed to
`com.securemicro.meu_servico` and `pom.xml` updated. Follow the
post-generation instructions printed by the CLI to configure Keycloak and
Vault paths.

### 3. Full local stack (Keycloak + Vault + service + Jaeger)

> Complete step-by-step guide: [`docs/quickstart/tmpl-rest-api-quickstart.md`](docs/quickstart/tmpl-rest-api-quickstart.md)
> *(available when TASK-024 is complete)*

```bash
cd reference-deploy
docker compose up -d
```

Once healthy, the stack exposes:

| Endpoint | Description |
|----------|-------------|
| `http://localhost:8080/actuator/health` | Service health probe |
| `http://localhost:8080/actuator/prometheus` | Micrometer metrics |
| `http://localhost:8081` | Keycloak Admin Console |
| `http://localhost:8200` | Vault UI (dev mode — DEV ONLY) |
| `http://localhost:16686` | Jaeger trace UI |

> **Warning:** The Docker Compose stack uses Vault in dev mode and a
> pre-configured Keycloak admin password. **This configuration is for local
> development only and must never be used in production.**

---

## Security Controls and Federal Alignment

Every template in this repository implements controls traceable to:

- **Executive Order 14028** — Improving the Nation's Cybersecurity (May 2021)
- **NIST SP 800-53 Revision 5** — Security and Privacy Controls for Information Systems
- **CISA Secure by Design Guidance** — Shifting the Balance of Cybersecurity Risk

The full control-to-clause mapping is documented in each template's
`control-mapping.md` and mirrored under `docs/control-mappings/`.

### Mandatory controls implemented in every Phase 1 template

| Control | Description | Federal Reference |
|---------|-------------|-------------------|
| Zero secrets in config | All secrets resolved at runtime via HashiCorp Vault | EO 14028 §4(e), NIST SC-12 |
| OIDC authentication | Keycloak JWT validation with MFA on reference realm | NIST IA-2, IA-5 |
| Structured audit logging | JSON audit trail for all write actions | NIST AU-2, AU-3, CISA |
| SBOM per build | CycloneDX bill of materials for supply chain compliance | EO 14028 §4(e) |
| Security headers | HSTS, CSP, X-Frame-Options, X-Content-Type-Options | NIST SC-8, CISA |
| STRIDE threat model | Per-template threat model with trust boundaries | NIST RA-3, CISA |
| CVE gate in CI | Build fails on critical/high CVE in dependencies | EO 14028 §4(e), NIST SI-2 |
| No stack traces in responses | GlobalExceptionHandler strips internal details | NIST SI-10, CISA |

---

## Running Tests

```bash
# Java modules (from repo root)
mvn test

# sme-cli (Python)
cd sme-cli
pip install -e ".[dev]"
pytest
```

---

## Contributing

1. Read `docs/` for architecture decisions and control mappings.
2. Each template must maintain `threat-model.md` and `control-mapping.md` —
   CI will fail if either file is absent or empty.
3. No secrets in any committed file — CI runs `trufflehog` on every PR.
4. Scope for this repository is Java 17 / Spring Boot only. See `spec.md` for
   the full out-of-scope list.

---

## License

See `LICENSE` file for terms. Templates are provided as-is for educational and
reference purposes; adopters are responsible for their own compliance posture.
