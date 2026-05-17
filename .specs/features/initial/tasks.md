# Tarefas — Scaffold Inicial e Template REST API (Phase 1)

## Resumo

- Total de tarefas: 30
- Tarefas paralelizáveis: 19
- Caminho crítico estimado: TASK-001 → TASK-002 → TASK-003 → TASK-004 → TASK-005 → TASK-006 → TASK-009 → TASK-013 → TASK-018 → TASK-022 → TASK-027 → TASK-029

### Distribuição por Risco
- Crítico: 7
- Alto: 12
- Médio: 9
- Baixo: 2

### Distribuição por QA
- full: 19
- wave: 9
- smoke: 2
- auto: 0

### Distribuição por Perfil
- frontend: 0
- backend: 22
- infra: 6
- misto: 2

## Legenda

- `[P]` = Paralelizável com outras `[P]` que não compartilham arquivos
- Esforço: S / M / L
- Tipo: lógica-negócio | crud-padrão | ui-puro | integração-externa | migration | config | refactor | infra | teste
- Risco: crítico | alto | médio | baixo
- QA: full | wave | smoke | auto
- Perfil: frontend | backend | infra | misto

---

## Tarefas

### TASK-001 — Estrutura de repositório: scaffold de diretórios e POMs raiz

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: —
- **Tipo**: config
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `pom.xml`
  - `.gitignore`
- **Descrição**: Criar o `pom.xml` raiz Multi-Module Maven declarando todos os módulos (`shared-controls`, `shared-observability`, `shared-identity`, `shared-secrets`, `tmpl-rest-api`). Incluir BOM do Spring Boot 3.3.x e Spring Cloud 2023.x. Criar `.gitignore` cobrindo target/, `.env`, `*.secret`, arquivos de IDE. Este pom.xml é pré-requisito de todos os módulos Maven.
- **Critério de verificação**: `mvn validate` na raiz executa sem erros e lista os 5 módulos declarados. `.gitignore` bloqueia commit de arquivos `*.secret` (verificável via `git check-ignore`).

---

### TASK-002 — Estrutura de repositório: diretórios reservados e README de status

- **Esforço**: S
- **Paralelizável**: Não
- **Depende de**: TASK-001
- **Tipo**: config
- **Risco**: baixo
- **QA**: smoke
- **Perfil**: infra
- **Arquivos**:
  - `tmpl-event-driven/README.md`
  - `tmpl-batch-job/README.md`
- **Descrição**: Criar os diretórios reservados `tmpl-event-driven/` e `tmpl-batch-job/` com READMEs declarando explicitamente status "Phase 2 — planejado", conforme AC INI-02. Cada README deve descrever o propósito do template e o link para o PRD. Nenhum código é gerado nesses diretórios.
- **Critério de verificação**: `ls tmpl-event-driven/README.md tmpl-batch-job/README.md` retorna os dois arquivos; cada README contém a string "Phase 2".

---

### TASK-003 — shared-controls: pom.xml e estrutura de módulo

- **Esforço**: S
- **Paralelizável**: Não
- **Depende de**: TASK-001
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `shared-controls/pom.xml`
  - `shared-controls/src/main/java/com/securemicro/shared/controls/SharedControlsAutoConfiguration.java`
- **Descrição**: Criar o `pom.xml` do módulo `shared-controls` herdando do POM raiz, declarando dependências: `spring-boot-starter-web`, `spring-boot-starter-validation`, `jakarta.servlet-api`. Criar a classe `SharedControlsAutoConfiguration` com `@Configuration` e `@AutoConfiguration` para habilitar o auto-configure via Spring Boot. Registrar em `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **Critério de verificação**: `mvn -pl shared-controls compile` executa sem erros. Classe `SharedControlsAutoConfiguration` presente e anotada corretamente.

---

### TASK-004 — shared-controls: security headers filter e global exception handler

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-003
- **Tipo**: lógica-negócio
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-controls/src/main/java/com/securemicro/shared/controls/SecurityHeadersFilter.java`
  - `shared-controls/src/main/java/com/securemicro/shared/controls/GlobalExceptionHandler.java`
- **Descrição**: Implementar `SecurityHeadersFilter` (WebFilter para WebFlux) que injeta nos headers de toda resposta: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'` — alinhado ao plano seção 5.3 e RN-10. Implementar `GlobalExceptionHandler` (`@ControllerAdvice`) que captura todas as exceções não tratadas e retorna respostas genéricas sem stack trace, conforme contratos da seção 5.2. Adicionar comentários `// CONTROL: RN-10` nos pontos de controle para rastreabilidade bidirecional (requisito INI-30).
- **Critério de verificação**: Teste unitário verifica que: (a) toda resposta contém os 4 headers de segurança; (b) exceção RuntimeException resulta em `{"error":"internal_error"}` com status 500 sem campo stackTrace no body. `mvn -pl shared-controls test` passa.

---

### TASK-005 — shared-observability: pom.xml e auto-configuração base

- **Esforço**: S
- **Paralelizável**: Não
- **Depende de**: TASK-001
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `shared-observability/pom.xml`
  - `shared-observability/src/main/java/com/securemicro/shared/observability/SharedObservabilityAutoConfiguration.java`
- **Descrição**: Criar `pom.xml` do módulo `shared-observability` declarando dependências: `logstash-logback-encoder:7.x`, `micrometer-registry-prometheus`, `opentelemetry-spring-boot-starter:2.x`, `spring-boot-starter-actuator`. Criar `SharedObservabilityAutoConfiguration` com beans base. Registrar no arquivo de auto-configure do Spring Boot. Este módulo deve ter graceful degradation para OTel: se o exporter não conectar, o boot não falha (AC INI-21).
- **Critério de verificação**: `mvn -pl shared-observability compile` sem erros. Classe de auto-configuração presente e registrada.

---

### TASK-006 — shared-observability: logging JSON estruturado e MDC propagation

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-005
- **Tipo**: lógica-negócio
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-observability/src/main/java/com/securemicro/shared/observability/RequestLoggingFilter.java`
  - `shared-observability/src/main/resources/logback-spring.xml`
- **Descrição**: Implementar `RequestLoggingFilter` (WebFilter) que: (1) gera ou propaga `X-Correlation-ID` no MDC; (2) extrai `auth.subject` do `SecurityContext` se autenticado; (3) ao completar a troca HTTP emite log JSON com todos os campos obrigatórios do plano seção 4.3: `timestamp`, `correlation-id`, `trace-id`, `span-id`, `http.method`, `http.path`, `http.status`, `duration_ms`, `auth.subject`, `service.name`, `service.version`. Configurar `logback-spring.xml` com `LogstashEncoder` para saída JSON estruturada. Adicionar comentários `// CONTROL: INI-16`.
- **Critério de verificação**: Teste unitário com `MockServerHttpRequest` verifica que o log emitido é JSON parseável contendo todos os campos obrigatórios do schema. `mvn -pl shared-observability test` passa.

---

### TASK-007 [P] — shared-observability: audit trail bean

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-005
- **Tipo**: lógica-negócio
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-observability/src/main/java/com/securemicro/shared/observability/AuditTrailService.java`
  - `shared-observability/src/test/java/com/securemicro/shared/observability/AuditTrailServiceTest.java`
- **Descrição**: Implementar `AuditTrailService` — bean Spring que expõe método `audit(subject, action, resource, result, reason)` e emite evento de log estruturado JSON com `event-type=AUDIT` e todos os campos do schema da seção 4.3: `timestamp`, `correlation-id`, `audit.subject`, `audit.action`, `audit.resource`, `audit.result`, `audit.reason`. Marcar com `// CONTROL: INI-17`. O service deve ser injetável nos controllers do template.
- **Critério de verificação**: Teste unitário verifica que ao chamar `audit(...)` é emitido exatamente um log com `event-type=AUDIT` e todos os campos presentes. `mvn -pl shared-observability test` passa.

---

### TASK-008 [P] — shared-observability: OTel trace propagation e graceful degradation

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-005
- **Tipo**: integração-externa
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-observability/src/main/java/com/securemicro/shared/observability/OtelConfiguration.java`
  - `shared-observability/src/main/resources/application-observability.yml`
- **Descrição**: Configurar o SDK OpenTelemetry via `OtelConfiguration` para exportar traces via OTLP HTTP para o endereço configurável `OTEL_EXPORTER_OTLP_ENDPOINT`. Implementar graceful degradation: se o exporter não conectar ao startup, o contexto Spring deve subir normalmente (AC INI-21). Configurar W3C TraceContext como propagador padrão (AC INI-19). Marcar com `// CONTROL: INI-19`, `// CONTROL: INI-21`. Adicionar `application-observability.yml` com valores default seguros.
- **Critério de verificação**: Teste de contexto Spring carrega sem Jaeger disponível e sem lançar exceção. Teste verifica que o propagador `W3CTraceContextPropagator` está registrado.

---

### TASK-009 — shared-identity: pom.xml e configuração de resource server OAuth2

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-001
- **Tipo**: integração-externa
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-identity/pom.xml`
  - `shared-identity/src/main/java/com/securemicro/shared/identity/IdentitySecurityConfiguration.java`
- **Descrição**: Criar `pom.xml` com dependência `spring-boot-starter-oauth2-resource-server`. Implementar `IdentitySecurityConfiguration` (`@Configuration`, `@EnableWebFluxSecurity`) que: (1) configura `JwtDecoder` usando `KEYCLOAK_ISSUER_URI` via env var — nunca hardcoded (AC INI-06); (2) valida assinatura (JWKS), expiração e audience (AC INI-07); (3) protege todos os paths exceto `/actuator/health/**` e `/actuator/prometheus` (AC INI-08); (4) retorna HTTP 401 para token ausente/inválido e HTTP 403 para role insuficiente com body genérico (plano seção 5.2). Marcar com `// CONTROL: INI-04`, `// CONTROL: INI-05`, `// CONTROL: INI-06`, `// CONTROL: INI-07`, `// CONTROL: RN-02`.
- **Critério de verificação**: Testes unitários com WireMock: (a) requisição sem token → 401 com body `{"error":"unauthorized"}`; (b) token válido role correta → request prossegue; (c) token válido role errada → 403 com body `{"error":"forbidden"}`; (d) `KEYCLOAK_ISSUER_URI` lida de env var, não de literal string no código. `mvn -pl shared-identity test` passa.

---

### TASK-010 [P] — shared-identity: extrator de claims e utilitários de autorização por role

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-009
- **Tipo**: lógica-negócio
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-identity/src/main/java/com/securemicro/shared/identity/JwtClaimsExtractor.java`
  - `shared-identity/src/test/java/com/securemicro/shared/identity/JwtClaimsExtractorTest.java`
- **Descrição**: Implementar `JwtClaimsExtractor` com métodos estáticos/utilitários para: (1) extrair `subject` do `JwtAuthenticationToken`; (2) verificar presença de role específica (`hasRole(token, role)`); (3) extrair lista de roles do claim `realm_access.roles` do Keycloak. Esses utilitários são usados pelos controllers do `tmpl-rest-api` e pelo `AuditTrailService`. Marcar com `// CONTROL: INI-05`.
- **Critério de verificação**: Teste unitário com Jwt mock: `getSubject()` retorna o sub correto; `hasRole(token, "ROLE_SERVICE_ADMIN")` retorna true/false corretamente com base nos claims. `mvn -pl shared-identity test` passa.

---

### TASK-011 — shared-secrets: pom.xml e bootstrap Spring Cloud Vault

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-001
- **Tipo**: integração-externa
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-secrets/pom.xml`
  - `shared-secrets/src/main/java/com/securemicro/shared/secrets/VaultBootstrapConfiguration.java`
- **Descrição**: Criar `pom.xml` com dependências `spring-cloud-vault-config:4.x` e `spring-cloud-starter-vault-config`. Implementar `VaultBootstrapConfiguration` que configura o Spring Cloud Vault para: (1) conectar via `VAULT_ADDR` e `VAULT_TOKEN` (token auth para dev) ou AppRole (documentado para produção); (2) resolver paths KV v2 configurados em `application.yml` via `spring.config.import=vault://`; (3) fail-fast com mensagem clara se Vault inacessível (edge case do spec, plano seção 10 item 12). Documentar na classe que KV v2 não suporta lease renewal automático — rotação deve ser feita por reescrita (risco R-01 do plano). Marcar com `// CONTROL: RN-01`, `// CONTROL: INI-12`.
- **Critério de verificação**: Teste de integração com Vault Testcontainer (ou WireMock da API Vault): (a) com Vault acessível, secrets são injetados como `@Value`; (b) com Vault inacessível, contexto Spring falha no startup com `ApplicationContext` não iniciado e mensagem de log clara. `mvn -pl shared-secrets test` passa.

---

### TASK-012 [P] — shared-secrets: mascaramento de secrets em logs

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-011
- **Tipo**: lógica-negócio
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `shared-secrets/src/main/java/com/securemicro/shared/secrets/SecretMaskingConverter.java`
  - `shared-secrets/src/test/java/com/securemicro/shared/secrets/SecretMaskingConverterTest.java`
- **Descrição**: Implementar `SecretMaskingConverter` — Logback `MessageConverter` que intercepta todas as mensagens de log e substitui padrões de secrets (tokens, passwords, client-secrets) por `***MASKED***`. Os padrões devem cobrir: valores resolvidos do Vault que foram registrados no contexto Spring durante o bootstrap. Integrar com o `logback-spring.xml` do `shared-observability`. Marcar com `// CONTROL: RN-01`, `// CONTROL: INI-11`.
- **Critério de verificação**: Teste unitário verifica que log contendo valor de secret conhecido resulta em saída com `***MASKED***` no lugar do valor. Teste de regressão garante que valores não-secret não são mascarados. `mvn -pl shared-secrets test` passa.

---

### TASK-013 — tmpl-rest-api: scaffold do módulo Spring Boot WebFlux

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-004, TASK-006, TASK-009, TASK-011
- **Tipo**: config
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/pom.xml`
  - `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/TmplRestApiApplication.java`
- **Descrição**: Criar `pom.xml` do `tmpl-rest-api` herdando do POM raiz e declarando dependências nos 4 módulos `shared-*`, `spring-boot-starter-webflux`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webflux-ui:2.x`. Criar `TmplRestApiApplication.java` com `@SpringBootApplication`. O módulo deve compilar e subir (mesmo sem Keycloak/Vault) — a integração com serviços externos é configurável via env vars.
- **Critério de verificação**: `mvn -pl tmpl-rest-api compile` sem erros. Todos os módulos `shared-*` resolvidos como dependências Maven locais.
- **Status**: ✅ APROVADA em 2026-05-16 — branch: feature/initial-TASK-013

---

### TASK-014 [P] — tmpl-rest-api: application.yml sem secrets e referências ao Vault

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: config
- **Risco**: crítico
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/config/application.yml`
  - `tmpl-rest-api/config/vault-paths.md`
- **Descrição**: Criar `application.yml` com todas as configurações do serviço, onde nenhum valor de secret aparece em texto claro. Todos os secrets devem ser referenciados via `spring.config.import=vault://secret/tmpl-rest-api/...`. Configurar: `KEYCLOAK_ISSUER_URI` como env var placeholder, paths do Vault para `keycloak.client-secret`, `service.api-key`. Criar `vault-paths.md` documentando cada path de Vault com o secret correspondente (AC INI-14). Marcar comentários YAML com `# CONTROL: RN-01`. Executar `trufflehog filesystem .` no arquivo — zero findings.
- **Critério de verificação**: `trufflehog filesystem tmpl-rest-api/config/application.yml` retorna zero findings. `vault-paths.md` contém os 3 paths documentados na seção 4.1 do plano.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-014

---

### TASK-015 [P] — tmpl-rest-api: controllers WebFlux placeholder (recursos e hello)

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: crud-padrão
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/api/ResourceController.java`
  - `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/api/HelloController.java`
- **Descrição**: Implementar `ResourceController` com: `GET /api/v1/resources` protegido por `ROLE_SERVICE_USER` e `POST /api/v1/resources` protegido por `ROLE_SERVICE_ADMIN` (que injeta `AuditTrailService` e emite audit event na criação). Implementar `HelloController` com `GET /api/v1/hello` protegido por `ROLE_SERVICE_USER`. Todos os endpoints retornam dados placeholder. Marcar com `// CONTROL: INI-04`, `// CONTROL: INI-05`, `// CONTROL: INI-17`.
- **Critério de verificação**: Testes unitários com `WebTestClient` mockando `SecurityContext`: (a) GET /api/v1/resources com role USER → 200; (b) POST /api/v1/resources com role ADMIN → 201 e audit log emitido; (c) GET /api/v1/hello com role USER → 200. `mvn -pl tmpl-rest-api test` passa.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-015

---

### TASK-016 [P] — tmpl-rest-api: health probes e métricas Actuator

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/config/ActuatorConfiguration.java`
  - `tmpl-rest-api/src/test/java/com/securemicro/tmpl/restapi/config/ActuatorConfigurationTest.java`
- **Descrição**: Configurar via `ActuatorConfiguration` e `application.yml` (seção management): (1) expor `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus` sem autenticação (ACs INI-08, INI-20, INI-18); (2) garantir que os health details não exponham informações sensíveis (versões internas, stack traces). Verificar que `/actuator/prometheus` retorna métricas com prefixos `http_server_requests`, `jvm_`. Marcar com `// CONTROL: RN-05`, `// CONTROL: INI-18`, `// CONTROL: INI-20`.
- **Critério de verificação**: Teste de integração: `GET /actuator/health` retorna 200 sem token; `GET /actuator/prometheus` retorna 200 sem token com corpo contendo `http_server_requests` e `jvm_memory`.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-016

---

### TASK-017 [P] — tmpl-rest-api: tratamento de erros e edge cases de segurança

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: lógica-negócio
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/src/main/java/com/securemicro/tmpl/restapi/security/SecurityErrorHandler.java`
  - `tmpl-rest-api/src/test/java/com/securemicro/tmpl/restapi/security/SecurityErrorHandlerTest.java`
- **Descrição**: Implementar `SecurityErrorHandler` que trata os edge cases definidos no spec: (1) token expirado → HTTP 401 com `{"error":"unauthorized"}` sem detalhe sobre motivo (edge case do spec); (2) Keycloak inacessível → HTTP 503 com `{"error":"service_unavailable"}` sem detalhe interno, mas com log interno detalhado; (3) garantir que stack traces nunca apareçam no body de resposta (RN-10). Marcar com `// CONTROL: RN-10`, `// CONTROL: INI-04`.
- **Critério de verificação**: Testes unitários: (a) Jwt expirado → 401 sem campo `exception` no body; (b) IDP inacessível → 503 com body genérico; (c) nenhum body de erro contém a string `stackTrace` ou `at com.`. `mvn -pl tmpl-rest-api test` passa.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-017

---

### TASK-018 — tmpl-rest-api: testes de integração com Keycloak WireMock

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-015, TASK-016, TASK-017
- **Tipo**: teste
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/src/test/java/com/securemicro/tmpl/restapi/integration/AuthenticationIntegrationTest.java`
  - `tmpl-rest-api/src/test/java/com/securemicro/tmpl/restapi/integration/AuditTrailIntegrationTest.java`
- **Descrição**: Implementar testes de integração usando WireMock para simular o JWKS endpoint do Keycloak: (1) `AuthenticationIntegrationTest` — verifica ACs INI-04, INI-05, INI-07, INI-08: 401 sem token, 403 com role errada, 200 com role correta, 200 no health sem token, 401 com token expirado; (2) `AuditTrailIntegrationTest` — verifica AC INI-17: POST /api/v1/resources com token ADMIN emite log com `event-type=AUDIT` contendo subject, action, timestamp e result.
- **Critério de verificação**: `mvn -pl tmpl-rest-api verify` passa com todos os testes de integração verdes. Critérios de aceite técnico 3 e 6 do plano verificados.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-018

---

### TASK-019 [P] — tmpl-rest-api: Dockerfile multi-stage e build reproduzível com Jib

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: infra
- **Risco**: alto
- **QA**: full
- **Perfil**: infra
- **Arquivos**:
  - `tmpl-rest-api/build/Dockerfile`
  - `tmpl-rest-api/pom.xml`
- **Descrição**: Configurar build de container reproduzível. Adicionar `cyclonedx-maven-plugin:2.x` e `jib-maven-plugin` no `pom.xml` do `tmpl-rest-api`. Criar `Dockerfile` multi-stage como referência alternativa ao Jib, usando `eclipse-temurin:17-jre-alpine` como base, usuário não-root, read-only filesystem. Configurar `jib` com `project.build.outputTimestamp` fixo para garantir reprodutibilidade de digest (AC INI-24). Adicionar plugin `cyclonedx-maven-plugin` para geração de SBOM em `target/bom.xml` (AC INI-22). Marcar com `# CONTROL: RN-03`, `# CONTROL: RN-09`.
- **Critério de verificação**: `mvn -pl tmpl-rest-api package cyclonedx:makeAggregateBom` gera `target/bom.xml` em formato CycloneDX. Dois builds consecutivos com mesmo código produzem imagem com mesmo digest (verificável com `docker inspect --format='{{.Id}}'`).
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-019

---

### TASK-020 [P] — tmpl-rest-api: documentação de threat model STRIDE

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: config
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/threat-model.md`
  - `docs/threat-models/tmpl-rest-api-threat-model.md`
- **Descrição**: Redigir o threat model STRIDE completo do `tmpl-rest-api` contendo obrigatoriamente (AC INI-29): (1) trust boundaries (cliente externo, serviço, Keycloak, Vault, serviços downstream); (2) atores (engenheiro, revisor AppSec, atacante externo); (3) fluxo de dados com diagrama textual; (4) ameaças STRIDE analisadas para cada componente; (5) ameaças explicitamente fora do escopo. O arquivo em `docs/threat-models/` é o espelho do que está no template (conforme arquitetura do plano). O CI deve verificar que este arquivo existe e não está vazio (AC INI-28).
- **Critério de verificação**: Ambos os arquivos existem, têm mais de 200 linhas e contêm as seções obrigatórias: "Trust Boundaries", "STRIDE", "Out of Scope". Gate CI do TASK-025 valida isso automaticamente.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-020 (3ª rodada manual após BLOQUEADA_NEEDS_HUMAN; fixes aplicados pelo orquestrador)

---

### TASK-021 [P] — tmpl-rest-api: control-mapping.md com rastreabilidade federal

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-013
- **Tipo**: config
- **Risco**: alto
- **QA**: full
- **Perfil**: backend
- **Arquivos**:
  - `tmpl-rest-api/control-mapping.md`
  - `docs/control-mappings/tmpl-rest-api-controls.md`
- **Descrição**: Redigir o mapeamento completo de controles para fontes federais (AC INI-27): cada controle implementado (RN-01 a RN-10, INI-04 a INI-21) mapeado à cláusula específica de EO 14028, NIST SP 800-53 rev5 ou CISA Guidance. Incluir referência ao arquivo e marcação `// CONTROL:` correspondente no código. O arquivo em `docs/control-mappings/` é o espelho do que está no template. A rastreabilidade bidirecional (INI-30) exige que cada ID listado aqui tenha um `// CONTROL: <ID>` no código implementado nas tasks anteriores.
- **Critério de verificação**: Arquivo contém tabela com colunas `Control ID`, `Descrição`, `Fonte Federal`, `Cláusula`, `Arquivo de Código`. Mínimo 15 controles mapeados. Gate CI do TASK-025 verifica presença de cada `CONTROL: <ID>` no código.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-021

---

### TASK-022 — reference-deploy: Docker Compose Keycloak + Vault + app + Jaeger

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-013, TASK-019
- **Tipo**: infra
- **Risco**: alto
- **QA**: full
- **Perfil**: infra
- **Arquivos**:
  - `reference-deploy/docker-compose.yml`
  - `reference-deploy/vault/vault-dev-init.sh`
- **Descrição**: Criar `docker-compose.yml` que sobe os 4 serviços com um único `docker compose up -d` (AC INI-32): `keycloak:24.x` com healthcheck, `vault:1.17.x` em modo dev com healthcheck, `tmpl-rest-api` aguardando Keycloak e Vault via `depends_on: condition: service_healthy`, `jaegertracing/all-in-one:1.57`. Criar `vault-dev-init.sh` que faz seed dos paths KV v2 (`secret/tmpl-rest-api/keycloak`, `secret/tmpl-rest-api/service`, `secret/tmpl-rest-api/db`) com valores de placeholder para dev (AC INI-33). Documentar explicitamente no compose e no script que modo dev NÃO é para produção. Marcar com `# CONTROL: RN-01`.
- **Critério de verificação**: `docker compose up -d` seguido de `docker compose ps` mostra todos os 4 serviços em estado `healthy` (ou running) sem intervenção manual. `vault-dev-init.sh` executa sem erros e paths ficam acessíveis via `vault kv get`.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-022

---

### TASK-023 [P] — reference-deploy: realm Keycloak com MFA obrigatório

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-022
- **Tipo**: integração-externa
- **Risco**: crítico
- **QA**: full
- **Perfil**: infra
- **Arquivos**:
  - `reference-deploy/keycloak/realm-export.json`
  - `reference-deploy/docker-compose.yml`
- **Descrição**: Criar `realm-export.json` do realm de referência do Keycloak conforme seção 4.2 do plano: `otpPolicyType: totp`, `browserFlow: browser-with-otp` (MFA obrigatório, AC INI-09), client `tmpl-rest-api` confidencial com `publicClient: false`, roles `ROLE_SERVICE_USER` e `ROLE_SERVICE_ADMIN`. O client-secret deve ser referenciado como instrução `${VAULT_MANAGED}` — não deve conter valor real (RN-01). Atualizar `docker-compose.yml` para importar o realm automaticamente via `--import-realm` no Keycloak. Marcar com `// CONTROL: INI-09`, `// CONTROL: RN-02`.
- **Critério de verificação**: `docker compose up -d` → Keycloak sobe com o realm importado; `GET /realms/tmpl-rest-api/.well-known/openid-configuration` retorna 200. Arquivo `realm-export.json` não contém nenhum valor de secret em texto claro (`trufflehog filesystem reference-deploy/keycloak/` → zero findings).
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-023 (2ª rodada QA após fixes de bind-mount e _comment/_control)

---

### TASK-024 [P] — docs: guia quickstart clone → rodando em menos de 30 minutos

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-022
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: misto
- **Arquivos**:
  - `docs/quickstart/tmpl-rest-api-quickstart.md`
  - `reference-deploy/docker-compose.yml`
- **Descrição**: Redigir o guia de quickstart (AC INI-31, INI-34) com pré-requisitos, sequência exata de comandos e resultados esperados: (1) verificar Docker instalado (edge case do spec — detectar ausência e exibir aviso); (2) `docker compose pull` para pré-download das imagens; (3) `docker compose up -d`; (4) obter token JWT do Keycloak via `curl`; (5) chamar endpoint autenticado; (6) ver log JSON no stdout; (7) acessar `/actuator/prometheus`; (8) acessar Jaeger UI em `localhost:16686`. Cada passo com comando exato e output esperado. Documentar explicitamente que Vault dev mode NÃO é para produção (AC INI-33) com referência às seções de produção do PRD (AC INI-35).
- **Critério de verificação**: Sequência de comandos do guia executada em máquina limpa (apenas Docker instalado) completa em menos de 30 minutos com imagens pré-baixadas. Cada curl retorna o status HTTP esperado documentado no guia.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-024 (2ª rodada QA wave após fix da inconsistência Opção B Vault↔Keycloak)

---

### TASK-025 — CI: pipeline GitHub Actions com gates de segurança

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-018, TASK-019, TASK-020, TASK-021
- **Tipo**: infra
- **Risco**: alto
- **QA**: full
- **Perfil**: infra
- **Arquivos**:
  - `.github/workflows/ci.yml`
  - `.github/workflows/sbom.yml`
- **Descrição**: Criar `ci.yml` com steps: (1) `mvn clean verify` — build + testes; (2) `trufflehog filesystem .` — varredura de secrets, falha se encontrar padrão (AC INI-15, RN-01); (3) `trivy fs --exit-code 1 --severity CRITICAL,HIGH .` — CVE scan, falha em CVE crítica ou alta (AC INI-25, RN-08); (4) script que verifica que `threat-model.md` existe e não está vazio (AC INI-28, RN-06); (5) script de rastreabilidade bidirecional: para cada `CONTROL: <ID>` no `control-mapping.md`, verifica que existe ao menos uma ocorrência de `// CONTROL: <ID>` no código (INI-30, plano risco R-04). Criar `sbom.yml` que gera o SBOM CycloneDX e o associa como artifact do workflow (AC INI-22, INI-26).
- **Critério de verificação**: Push em branch dispara o workflow; build falha intencionalmente ao: (a) introduzir secret em texto claro em qualquer arquivo; (b) `threat-model.md` ser esvaziado; (c) dependência com CVE crítica conhecida ser adicionada. Build passa no estado limpo do repositório.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-025

---

### TASK-026 [P] — sme-cli: scaffold do projeto Python e estrutura de módulos

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-001
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `sme-cli/pyproject.toml`
  - `sme-cli/sme/__init__.py`
- **Descrição**: Criar `pyproject.toml` do projeto Python 3.11+ com: entry point `sme = sme.cli:main`, dependências `click` (ou argparse), versão `0.1.0`. Criar `sme/__init__.py` com `__version__ = "0.1.0"`. Criar estrutura de diretórios: `sme/`, `sme/commands/`, `tests/`. O CLI deve ser instalável via `pip install -e .` e executável como `sme`.
- **Critério de verificação**: `pip install -e sme-cli/` instala sem erros. `sme --help` exibe mensagem de uso. Estrutura de diretórios conforme plano seção 3.1.

---

### TASK-027 — sme-cli: comando `sme --version` e entry point

- **Esforço**: S
- **Paralelizável**: Não
- **Depende de**: TASK-026
- **Tipo**: crud-padrão
- **Risco**: baixo
- **QA**: smoke
- **Perfil**: backend
- **Arquivos**:
  - `sme-cli/sme/cli.py`
  - `sme-cli/sme/templates.py`
- **Descrição**: Implementar `cli.py` com o grupo de comandos principal e o flag `--version` que exibe a versão lida de `__version__` (AC INI-38). Implementar `templates.py` com o registro de templates disponíveis: dicionário `AVAILABLE_TEMPLATES = {"tmpl-rest-api": {"path": "../tmpl-rest-api", "description": "..."}}`. Este registro é usado pelo comando `new` para validar templates existentes (AC INI-37) e pelo handler de erro de template inexistente.
- **Critério de verificação**: `sme --version` exibe `sme 0.1.0`. `sme new template-inexistente foo` exibe mensagem de erro listando `tmpl-rest-api` como template disponível e termina com exit code != 0.

---

### TASK-028 [P] — sme-cli: comando `sme new` — scaffold e renomeação de pacotes

- **Esforço**: M
- **Paralelizável**: Sim
- **Depende de**: TASK-027
- **Tipo**: lógica-negócio
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `sme-cli/sme/commands/new.py`
  - `sme-cli/tests/test_new_command.py`
- **Descrição**: Implementar `commands/new.py` com a lógica do `sme new tmpl-rest-api <project-name>` (ACs INI-35, INI-36): (1) validar que template existe no registro de `templates.py`; (2) validar que diretório `<project-name>` NÃO existe — abortar com erro sem sobrescrever (edge case do spec); (3) copiar o conteúdo do template para `./<project-name>/`; (4) substituir recursivamente `com.securemicro.tmpl.restapi` por `com.securemicro.<project-name>` em todos os arquivos `.java`, `.xml`, `.yml`; (5) atualizar `artifactId` e `<name>` no `pom.xml` para `<project-name>` (AC INI-36); (6) exibir instruções manuais pós-geração: configurar Keycloak client e Vault paths (premissa P-06).
- **Critério de verificação**: Teste automatizado: `sme new tmpl-rest-api meu-servico` cria diretório `meu-servico/`, não contém a string `com.securemicro.tmpl.restapi` em nenhum arquivo, `pom.xml` contém `<artifactId>meu-servico</artifactId>`, e `mvn -f meu-servico/pom.xml package` compila sem erros. Teste: executar com diretório existente → exit code != 0 sem modificar conteúdo.

---

### TASK-029 — Testes de integração end-to-end do quickstart (validação do fluxo completo)

- **Esforço**: M
- **Paralelizável**: Não
- **Depende de**: TASK-022, TASK-023, TASK-024, TASK-025
- **Tipo**: teste
- **Risco**: alto
- **QA**: full
- **Perfil**: misto
- **Arquivos**:
  - `reference-deploy/docker-compose.yml`
  - `docs/quickstart/tmpl-rest-api-quickstart.md`
- **Descrição**: Executar e validar o fluxo completo de quickstart conforme critérios de aceite técnicos 1–12 do plano seção 10: (1) `docker compose up -d` → todos os serviços healthy; (2) `curl` no Keycloak para obter token JWT; (3) `curl` nos endpoints autenticados com as respostas esperadas; (4) verificar log JSON estruturado no stdout do container; (5) `curl /actuator/prometheus` retorna métricas com prefixos `http_server_requests` e `jvm_`; (6) Jaeger UI em `localhost:16686` exibe traces; (7) `trufflehog filesystem .` → zero findings no repositório completo. Documentar o tempo total medido no guia de quickstart.
- **Critério de verificação**: Todos os 12 critérios de aceite técnicos do plano seção 10 verificados manualmente e documentados. Tempo total do quickstart ≤ 30 minutos com imagens pré-baixadas (AC INI-31). `trufflehog` retorna zero findings.

---

### TASK-030 [P] — README raiz e status de componentes

- **Esforço**: S
- **Paralelizável**: Sim
- **Depende de**: TASK-002, TASK-027
- **Tipo**: config
- **Risco**: médio
- **QA**: wave
- **Perfil**: backend
- **Arquivos**:
  - `README.md`
  - `sme-cli/README.md`
- **Descrição**: Criar `README.md` raiz do repositório com: (1) descrição do projeto SecureMicro-Energy-Templates e propósito; (2) tabela de status de todos os componentes declarando explicitamente "Phase 1 — implementado" ou "Phase 2 — planejado" (AC INI-02); (3) link para o guia de quickstart; (4) pré-requisitos (Docker, Java 17, Maven, Python 3.11); (5) referências às fontes federais (EO 14028, NIST SP 800-53, CISA). Criar `sme-cli/README.md` com instruções de instalação e uso do CLI.
- **Critério de verificação**: `README.md` contém tabela com todos os componentes listados na estrutura da seção 3.1 do plano, cada um com status explícito. Contém link funcional para `docs/quickstart/tmpl-rest-api-quickstart.md`.
- **Status**: ✅ APROVADA em 2026-05-17 — branch: feature/initial-TASK-030

---

## Grupos de Paralelização Sugeridos

- **Onda 1** (pode começar imediatamente): TASK-001

- **Onda 2** (após TASK-001): TASK-002, TASK-003, TASK-005, TASK-009, TASK-011, TASK-026
  - TASK-002: diretórios reservados
  - TASK-003: scaffold shared-controls
  - TASK-005: scaffold shared-observability
  - TASK-009: scaffold shared-identity (crítico — inicia imediatamente após TASK-001)
  - TASK-011: scaffold shared-secrets (crítico — inicia imediatamente após TASK-001)
  - TASK-026: scaffold sme-cli (independente do Java)

- **Onda 3** (dependências da Onda 2):
  - TASK-004 (depende de TASK-003): security headers e exception handler
  - TASK-006 (depende de TASK-005): logging JSON e MDC
  - TASK-007 [P] e TASK-008 [P] (dependem de TASK-005, paralelizáveis entre si): audit trail e OTel
  - TASK-010 [P] (depende de TASK-009): extrator de claims
  - TASK-012 [P] (depende de TASK-011): mascaramento de secrets
  - TASK-027 (depende de TASK-026): sme CLI entry point e --version

- **Onda 4** (após TASK-004, TASK-006, TASK-009, TASK-011):
  - TASK-013: scaffold tmpl-rest-api (pré-requisito de toda a onda 5)

- **Onda 5** (após TASK-013 — todas paralelizáveis entre si):
  - TASK-014 [P]: application.yml e vault-paths.md
  - TASK-015 [P]: controllers WebFlux
  - TASK-016 [P]: health probes e métricas
  - TASK-017 [P]: tratamento de erros de segurança
  - TASK-019 [P]: Dockerfile e build reproduzível
  - TASK-020 [P]: threat model STRIDE
  - TASK-021 [P]: control-mapping.md
  - TASK-028 [P]: comando `sme new` (depende de TASK-027, pode rodar em paralelo com a onda 5)

- **Onda 6** (após TASK-015, TASK-016, TASK-017):
  - TASK-018: testes de integração com WireMock

- **Onda 7** (após TASK-018, TASK-019, TASK-020, TASK-021):
  - TASK-022: Docker Compose reference-deploy
  - TASK-025: pipeline CI com gates de segurança

- **Onda 8** (após TASK-022):
  - TASK-023 [P]: realm Keycloak com MFA
  - TASK-024 [P]: guia quickstart
  - TASK-030 [P]: README raiz (pode iniciar após TASK-002 e TASK-027)

- **Onda 9** (após TASK-022, TASK-023, TASK-024, TASK-025):
  - TASK-029: validação end-to-end do quickstart
