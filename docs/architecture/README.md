# Architecture — SecureMicro-Energy-Templates

Visual reference for how the templates, shared libraries, and runtime
dependencies fit together. Diagrams use [Mermaid](https://mermaid.js.org/) and
render natively on GitHub.

---

## 1. System context (C4 — Level 1)

How a scaffolded service sits between the operator, the federal-aligned control
plane, and the observability backends.

```mermaid
C4Context
    title System Context — a service scaffolded from tmpl-rest-api

    Person(operator, "Platform / AppSec Engineer", "Scaffolds and operates services")
    Person(client, "API Client", "Authenticated caller (OIDC bearer token)")

    System_Boundary(sme, "SecureMicro service") {
        System(svc, "tmpl-rest-api service", "Spring Boot 3 WebFlux + shared-* libs")
    }

    System_Ext(kc, "Keycloak", "OIDC IdP — issues & signs JWTs")
    System_Ext(vault, "HashiCorp Vault", "KV v2 secrets at runtime")
    System_Ext(jaeger, "Jaeger", "OTLP trace sink")
    System_Ext(prom, "Prometheus", "Scrapes Micrometer metrics")

    Rel(operator, svc, "scaffolds via sme-cli, operates")
    Rel(client, svc, "HTTPS + Bearer JWT")
    Rel(svc, kc, "Validates JWT signature (JWKS)")
    Rel(svc, vault, "Reads secrets at startup", "spring.config.import")
    Rel(svc, jaeger, "Exports spans", "OTLP/HTTP :4318")
    Rel(prom, svc, "Scrapes", "/actuator/prometheus")
```

---

## 2. Module / dependency graph (C4 — Level 2)

Maven reactor. Every Phase 1 template depends on the four `shared-*` libraries;
`sme-cli` consumes the templates as scaffold sources.

```mermaid
flowchart TD
    subgraph templates["Templates"]
        rest["tmpl-rest-api<br/>(Phase 1)"]
        batch["tmpl-batch-job<br/>(Phase 2 — planned)"]
        event["tmpl-event-driven<br/>(Phase 2 — planned)"]
    end

    subgraph shared["Shared libraries (JARs)"]
        ctrl["shared-controls<br/>SecurityHeadersFilter<br/>GlobalExceptionHandler"]
        obs["shared-observability<br/>JSON logs · Micrometer · OTel<br/>AuditTrailService"]
        id["shared-identity<br/>Keycloak JWT · RBAC"]
        sec["shared-secrets<br/>Spring Cloud Vault KV v2"]
    end

    cli["sme-cli (Python)<br/>scaffold generator"]
    deploy["reference-deploy<br/>docker-compose"]

    rest --> ctrl & obs & id & sec
    batch -.-> ctrl & obs & id & sec
    event -.-> ctrl & obs & id & sec
    cli -->|copies + renames packages| rest
    deploy -->|runs| rest

    classDef planned stroke-dasharray: 5 5,fill:#f6f6f6;
    class batch,event planned;
```

---

## 3. Authentication & authorization flow

OIDC bearer token validation on the WebFlux resource server. Maps to NIST
**IA-2 / IA-5** and demonstrates the 401 / 403 / 200 decision tree validated in
TASK-029 (AC-3).

```mermaid
sequenceDiagram
    autonumber
    participant C as API Client
    participant S as tmpl-rest-api<br/>(resource server)
    participant K as Keycloak (JWKS)

    C->>S: GET /api/v1/resources (Bearer JWT)
    alt no token
        S-->>C: 401 Unauthorized
    else token present
        S->>K: Fetch / cache JWKS public keys
        K-->>S: Signing keys
        S->>S: Validate signature, issuer, exp, audience
        alt invalid / expired
            S-->>C: 401 Unauthorized
        else valid but missing role
            S-->>C: 403 Forbidden
        else valid + role OK
            S->>S: Emit AUDIT event (subject/action/resource/result)
            S-->>C: 200 OK
        end
    end
    Note over S,K: Keycloak unreachable → 503 (fail-safe, see SecurityErrorHandler)
```

---

## 4. Secrets-at-runtime flow (zero secrets in config)

Maps to EO 14028 §4(e) and NIST **SC-12**. `fail-fast: true` means the context
refuses to start if Vault is unreachable — validated by `VaultFailFastTest`
(AC-12).

```mermaid
sequenceDiagram
    autonumber
    participant Boot as Spring Boot startup
    participant V as HashiCorp Vault (KV v2)
    participant App as Application context

    Boot->>V: spring.config.import=vault://... (NO optional:)
    alt Vault reachable
        V-->>Boot: secret payload (KV v2)
        Boot->>App: Inject resolved secrets, start
        App-->>App: Mask secret values in all logs
    else Vault unreachable + fail-fast=true
        V--xBoot: connection refused
        Boot--xApp: Context FAILS to start (no degraded mode)
    end
```

---

## 5. Observability pipeline

Three signals from one service: structured JSON logs, Micrometer/Prometheus
metrics, OpenTelemetry traces. Maps to NIST **AU-2 / AU-3** and INI-18/19/21.

```mermaid
flowchart LR
    svc["tmpl-rest-api"]
    svc -->|JSON via logstash-logback-encoder| logs["stdout / log sink<br/>event-type=AUDIT on writes"]
    svc -->|/actuator/prometheus| prom["Prometheus"]
    svc -->|OTLP/HTTP :4318| jaeger["Jaeger UI :16686"]
    prom --> graf["Dashboards / alerts"]
```

---

## Federal control alignment

Each architectural element traces to a federal clause. Full bidirectional
mapping (control ↔ code) lives in
[`docs/control-mappings/tmpl-rest-api-controls.md`](../control-mappings/tmpl-rest-api-controls.md)
and the per-template [`threat-model.md`](../threat-models/tmpl-rest-api-threat-model.md).

| Element | Control | Federal reference |
|---|---|---|
| OIDC auth flow (§3) | IA-2, IA-5 | NIST SP 800-53 Rev 5 |
| Vault secrets (§4) | SC-12 | EO 14028 §4(e) |
| Audit / metrics / traces (§5) | AU-2, AU-3 | NIST SP 800-53 Rev 5 |
| Security headers (shared-controls) | SC-8 | NIST, CISA Secure by Design |
| SBOM per build | — | EO 14028 §4(e) |
