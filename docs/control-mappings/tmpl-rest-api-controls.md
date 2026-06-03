# Control Mapping — tmpl-rest-api

**Template:** tmpl-rest-api  
**Phase:** 1  
**Status:** Phase 1 — implementado  
**Data:** 2026-05-17  
**Referencia:** INI-27, INI-30 (INI-US-06)

---

## Propósito

Este documento mapeia cada controle de segurança implementado no template
`tmpl-rest-api` à sua fonte federal correspondente (EO 14028, NIST SP 800-53
Rev 5, CISA Secure by Design). Para cada controle, a coluna **Arquivo de
Código** referencia o arquivo Java, YAML ou XML que contém o marcador
`// CONTROL:` correspondente, garantindo rastreabilidade bidirecional entre
requisito federal e implementação (INI-30).

---

## Fontes Federais

| Sigla | Documento |
|-------|-----------|
| EO 14028 | Executive Order 14028 on Improving the Nation's Cybersecurity (2021) |
| NIST SP 800-53 r5 | NIST Special Publication 800-53 Revision 5: Security and Privacy Controls |
| CISA | CISA Secure by Design Guidance (2023) |

---

## Tabela de Controles

| Control ID | Descrição | Fonte Federal | Cláusula | Arquivo de Código |
|------------|-----------|---------------|----------|-------------------|
| RN-01 | Secrets exclusivamente via HashiCorp Vault — zero valores hardcoded em variáveis de ambiente, arquivos de configuração ou VCS. `spring.config.import=vault://` sem prefixo `optional:` garante fail-fast se o Vault estiver indisponível. | NIST SP 800-53 r5 | IA-5 (Authenticator Management) | `tmpl-rest-api/config/application.yml` (`# CONTROL: RN-01`); `shared-secrets/src/main/java/com/securemicro/shared/secrets/VaultBootstrapConfiguration.java` (`// CONTROL: RN-01`) |
| RN-02 | Autenticação mandatória para todos os endpoints não-públicos via Keycloak OIDC JWT. O filter chain rejeita qualquer requisição sem token bearer válido antes de atingir o controller. | NIST SP 800-53 r5 | AC-3 (Access Enforcement) | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: RN-02`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/TmplRestApiApplication.java` (`// CONTROL: RN-02`) |
| RN-03 | Dependências de software verificadas e declaradas em SBOM no formato CycloneDX gerado automaticamente pelo `cyclonedx-maven-plugin` na fase `package`. Garante proveniência de software conforme EO 14028. | EO 14028 §4(e); NIST SP 800-53 r5 | SR-4 (Provenance) | `pom.xml` (`CONTROL: RN-03 \| INI-22`); `tmpl-rest-api/build/Dockerfile` (`# CONTROL: RN-03`) |
| RN-04 | Audit log imutável — eventos de audit trail são emitidos via `AuditTrailService` como entradas de log estruturado JSON e encaminhados ao agregador de logs (Elastic/Splunk/Loki). A imutabilidade é garantida pelo design: os eventos são apenas append no log agregado. | NIST SP 800-53 r5 | AU-9 (Protection of Audit Information) | `shared-observability/src/main/java/com/securemicro/shared/observability/AuditTrailService.java` (`// CONTROL: RN-04`) |
| RN-05 | Health probes liveness (`/actuator/health/liveness`) e readiness (`/actuator/health/readiness`) presentes e funcionais, acessíveis sem autenticação para uso por Kubernetes e load balancers. | NIST SP 800-53 r5 | SI-10 (Information Input Validation — probe presente e funcional) | `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/config/ActuatorConfiguration.java` (`// CONTROL: RN-05`); `tmpl-rest-api/config/application.yml` (`# CONTROL: RN-05`) |
| RN-09 | Imagem de container construída sobre `eclipse-temurin:17-jre-alpine` (JRE minimal, sem JDK ou ferramentas de build em runtime). Jib produz imagem reproduzível com digest determinístico via `project.build.outputTimestamp` fixo. | EO 14028 §4(i); NIST SP 800-53 r5 | SR-4 (Provenance) | `tmpl-rest-api/build/Dockerfile` (`# CONTROL: RN-09`); `pom.xml` (`CONTROL: RN-09 \| INI-24`) |
| RN-10 | Stack traces nunca expostos em respostas HTTP. O `GlobalExceptionHandler` captura todas as exceções não tratadas e retorna corpo genérico `{"error":"internal_error"}`. O log interno completo (mensagem + stack trace) é emitido para análise pós-incidente. | NIST SP 800-53 r5 | SI-10 (Information Input Validation — vazamento de informação interna) | `shared-controls/src/main/java/com/securemicro/shared/controls/GlobalExceptionHandler.java` (`// CONTROL: RN-10`); `tmpl-rest-api/config/application.yml` (`server.error.include-stacktrace: never`) |
| INI-04 | HTTP 401 retornado para requisições sem token JWT válido (ausente, expirado, assinatura inválida). Corpo da resposta é genérico: `{"error":"unauthorized"}` — nenhum detalhe interno exposto. | NIST SP 800-53 r5 | AC-3 (Access Enforcement); IA-9 (Service Identification and Authentication) | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: INI-04`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/api/ResourceController.java` (`// CONTROL: INI-04`) |
| INI-05 | HTTP 403 retornado para tokens JWT válidos cujo portador não possui a role exigida (`@PreAuthorize`). Corpo genérico: `{"error":"forbidden"}`. `@EnableReactiveMethodSecurity` habilita enforcement no nível de método. | NIST SP 800-53 r5 | AC-3 (Access Enforcement) | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: INI-05`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/api/ResourceController.java` (`// CONTROL: INI-05`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/TmplRestApiApplication.java` (`// CONTROL: INI-05`) |
| INI-06 | Issuer URI do Keycloak resolvida exclusivamente da variável de ambiente `KEYCLOAK_ISSUER_URI` — nunca hardcoded em código ou arquivo de configuração versionado. Ausência da variável no startup causa falha imediata (fail-fast). | NIST SP 800-53 r5 | CM-6 (Configuration Settings) | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: INI-06`); `tmpl-rest-api/config/application.yml` (`issuer-uri: ${KEYCLOAK_ISSUER_URI}`) |
| INI-07 | Validação completa do JWT: assinatura (via JWKS do Keycloak), expiração (`exp`), not-before (`nbf`), issuer (`iss`) e audience (`aud`). A validação de `aud` previne Token Confusion Attacks (CWE-284) onde token emitido para outro cliente do mesmo realm seria aceito. | NIST SP 800-53 r5 | IA-5 (Authenticator Management); SC-8 (Transmission Confidentiality and Integrity) | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: INI-07` — em `jwtDecoder()`, `securityWebFilterChain()` e campos `issuerUri`, `expectedAudience`) |
| INI-08 | Endpoints `/actuator/health/**` e `/actuator/prometheus` acessíveis sem autenticação. Permite Kubernetes probes e scraping do Prometheus sem expor token ou credencial. | CISA Secure by Design §2.1 (Eliminate Default Passwords / Secure Defaults) | §2.1 | `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java` (`// CONTROL: INI-08`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/config/ActuatorConfiguration.java` (`// CONTROL: INI-20` — endpoint exposto) |
| INI-09 | MFA obrigatório no realm de referência do Keycloak — `otpPolicyType: totp` configurado no realm-export com `CONFIGURE_TOTP` como required action para novos usuários. | NIST SP 800-53 r5 | IA-5(1) (Authenticator Management — Password-Based Authentication); EO 14028 §3(b) (MFA obrigatório para sistemas federais) | `reference-deploy/keycloak/realm-export.json` (`otpPolicyType: totp`; campo `requiredActions` contendo `CONFIGURE_TOTP`) |
| INI-16 | Logging estruturado em JSON via Logstash Logback Encoder. Cada requisição emite evento com: `timestamp`, `correlation-id`, `trace-id`, `method`, `path`, `status`, `duration-ms`, `auth.subject`. Configurado em `logback-spring.xml`. | NIST SP 800-53 r5 | AU-2 (Event Logging) | `shared-observability/src/main/java/com/securemicro/shared/observability/RequestLoggingFilter.java` (`// CONTROL: INI-16`); `shared-observability/src/main/resources/logback-spring.xml` (`CONTROL: INI-16`) |
| INI-17 | Audit trail em operações de modificação de dados — `AuditTrailService.audit()` emite evento estruturado com: `audit.subject` (JWT sub), `audit.action`, `audit.resource`, `audit.result`, `audit.reason`. Campos MDC removidos no `finally` para evitar leakage. | NIST SP 800-53 r5 | AU-12 (Audit Record Generation) | `shared-observability/src/main/java/com/securemicro/shared/observability/AuditTrailService.java` (`// CONTROL: INI-17`); `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/api/ResourceController.java` (`// CONTROL: INI-17`) |
| INI-18 | Endpoint Prometheus (`/actuator/prometheus`) exposto com métricas Micrometer: taxa de requisições, latência (p50/p95/p99), erros por tipo, JVM heap, GC e threads. Habilitado via `management.metrics.export.prometheus.enabled: true`. | NIST SP 800-53 r5 | SI-4 (System Monitoring) | `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/config/ActuatorConfiguration.java` (`// CONTROL: INI-18`); `tmpl-rest-api/config/application.yml` (`# CONTROL: INI-18`) |
| INI-19 | Distributed tracing via OpenTelemetry com propagação de W3C TraceContext. `OtelConfiguration` configura o SDK com sampling probability 1.0 e exportação via OTLP HTTP. `RequestLoggingFilter` inclui `trace-id` em cada evento de log. | NIST SP 800-53 r5 | AU-2 (Event Logging — correlação de eventos entre serviços) | `shared-observability/src/main/java/com/securemicro/shared/observability/OtelConfiguration.java` (`// CONTROL: INI-19`); `tmpl-rest-api/config/application.yml` (`# CONTROL: INI-19`) |
| INI-20 | Liveness probe (`/actuator/health/liveness`) e readiness probe (`/actuator/health/readiness`) configurados via `management.endpoint.health.probes.enabled: true`. Sem autenticação — acessíveis por Kubernetes. | NIST SP 800-53 r5 | SI-4 (System Monitoring); RN-05 (probe presente e funcional) | `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/config/ActuatorConfiguration.java` (`// CONTROL: INI-20`); `tmpl-rest-api/config/application.yml` (`# CONTROL: INI-20`) |
| INI-21 | Graceful degradation quando coletor OpenTelemetry está indisponível. `OtelConfiguration` configura o SDK sem falhar no boot se `OTEL_EXPORTER_OTLP_ENDPOINT` não estiver definido ou inacessível. Testes validam startup sem coletor ativo. | NIST SP 800-53 r5 | SI-4 (System Monitoring) | `shared-observability/src/main/java/com/securemicro/shared/observability/OtelConfiguration.java` (`// CONTROL: INI-21`); `tmpl-rest-api/config/application.yml` (`# CONTROL: INI-21`) |
| INI-22 | SBOM gerado no formato CycloneDX via `cyclonedx-maven-plugin` na fase `package`. O artifact `bom.xml` inclui todas as dependências diretas e transitivas com versões pinadas. Conforme EO 14028 §4(e) para proveniência de software. | EO 14028 §4(e); NIST SP 800-53 r5 | SR-4 (Provenance) | `pom.xml` (`CONTROL: RN-03 \| INI-22` — declaração do `cyclonedx-maven-plugin` no `pluginManagement`) |
| INI-24 | Build reproduzível via Jib (`jib-maven-plugin`) com `project.build.outputTimestamp` fixado em `2026-01-01T00:00:00Z`. Garante que duas execuções do build com mesmo código e dependências produzem imagem com mesmo digest OCI. | NIST SP 800-53 r5 | SR-4 (Provenance) | `pom.xml` (`CONTROL: RN-09 \| INI-24` — declaração do `jib-maven-plugin`); `tmpl-rest-api/pom.xml` (`<project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>`) |

---

## Notas de Implementação

### INI-09 — Realm Keycloak e MFA

O arquivo `reference-deploy/keycloak/realm-export.json` é o artefato que
implementa INI-09. Ele está planejado como parte da fase de documentação
do reference-deploy (ver `plan.md` seção 4.2). A configuração inclui
`otpPolicyType: totp` e `CONFIGURE_TOTP` como required action, tornando
MFA obrigatório para todos os usuários do realm de referência.

### RN-04 — Imutabilidade do Audit Log

A imutabilidade é garantida pela arquitetura do pipeline de logs: os
eventos emitidos via `AuditTrailService` são gravados em stdout como JSON
estruturado e coletados pelo log aggregator (Elastic/Splunk/Loki) que
opera em modo append-only. Não há API de exclusão ou modificação de eventos
expostos pelo template.

### Controles Não Listados (fora do escopo de TASK-021)

Os controles INI-10 (credenciais Keycloak via Vault), INI-11 (zero secrets
em VCS), INI-12 (secrets resolvidos via Vault), INI-13 (lease renewal),
INI-14 (documentação de paths Vault), INI-15 (CI com varredura de secrets),
INI-23 (SBOM com dependências completas), INI-25 (CI falha em CVE crítica)
e INI-26 (SBOM associado à imagem) são controles que dependem de
configuração de CI/CD externa ao código do template e serão documentados
em tasks subsequentes.

### RN-06 — Verificação de Integridade de Artefato no CI

RN-06 é implementado no pipeline CI, fora do escopo deste repositório de
aplicação. A verificação de integridade (checksum/assinatura de artefato)
será configurada nas definições de pipeline quando criadas em TASK-025.
Referência: `.github/workflows/` ou `Jenkinsfile` (a serem criados em
TASK-025).

### RN-07 — Mapeamento de Controles (este documento)

Este documento em si é a implementação do controle RN-07 (rastreabilidade
bidirecional entre requisitos federais e código). Auto-referência como
entrada na tabela não é aplicável — o documento não pode rastrear a si
mesmo como artefato de código.

---

## Rastreabilidade Bidirecional

Para cada Control ID nesta tabela, o código correspondente contém um
marcador `// CONTROL: <ID>` (Java/YAML) ou `# CONTROL: <ID>` (Dockerfile/YAML)
na linha mais próxima à implementação do controle. A busca inversa pode ser
realizada com:

```bash
grep -r "CONTROL: <ID>" .
```

Exemplo:

```bash
grep -r "CONTROL: INI-07" shared-identity/
# Retorna: IdentitySecurityConfiguration.java com múltiplas ocorrências
```
