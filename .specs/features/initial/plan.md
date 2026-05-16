# Plano Técnico — Scaffold Inicial e Template REST API (Phase 1)

## 1. Resumo Executivo

A feature `initial` estabelece toda a fundação do repositório SecureMicro-Energy-Templates: a estrutura de diretórios padrão, os quatro módulos compartilhados (`shared-controls`, `shared-observability`, `shared-identity`, `shared-secrets`), o template `tmpl-rest-api` end-to-end, a infraestrutura de CI com gates de segurança, a documentação de controles federais e o protótipo do `sme-cli`. Ao final desta fase, qualquer engenheiro de energia deve conseguir clonar o repositório, executar um único comando Docker Compose e ter Keycloak, Vault e o serviço REST API respondendo a requisições autenticadas em menos de 30 minutos.

O impacto direto é a eliminação do gap entre o que regulações federais (EO 14028, NIST SP 800-53, CISA) exigem e o que equipes sem time de platform engineering conseguem implementar. O template codifica os controles como código executável e auditável, não como runbook. Cada controle implementado é rastreável a uma cláusula federal específica via `control-mapping.md`.

A stack é Java 17 / Spring Boot (WebFlux), que é a única linguagem suportada no v1 conforme PRD. Nenhuma linguagem nova, framework novo ou biblioteca fora do ecossistema Spring/Maven será introduzida sem justificativa explícita e marcação de risco.

O repositório está atualmente em estado inicial (apenas spec e PRD existem). Esta feature cria o repositório inteiro do zero — não há código legado a preservar, mas os padrões do PRD e do spec funcionam como restrições arquiteturais absolutas.

---

## 2. Premissas e Lacunas do Spec

### Premissas adotadas

| ID | Premissa |
|----|----------|
| P-01 | Stack: Java 17 + Spring Boot 3.x (WebFlux) + Maven Multi-Module. Alinha ao PRD seção 5.2 e CLAUDE.md (Spring Boot 2.7–3.0, Java 17). |
| P-02 | O `sme-cli` será implementado em Python 3.11+ (mesma versão já usada no monorepo), usando CLI via argparse ou Click — sem nova linguagem. Justificativa: é a linguagem de script mais presente no monorepo; Node.js seria alternativa aceitável. |
| P-03 | O módulo `shared-secrets` usará Spring Cloud Vault (integração Spring nativa com HashiCorp Vault) como mecanismo de lease renewal, sem Vault Agent Sidecar — decisão que resolve LACUNA-03. Vault Agent Sidecar exigiria orquestrador Kubernetes, fora do escopo de Phase 1. |
| P-04 | O formato SBOM preferencial será CycloneDX, gerado pelo `cyclonedx-maven-plugin`. CycloneDX tem suporte nativo no Maven ecosystem e é referenciado por CISA. Isso resolve LACUNA-04. |
| P-05 | O reference-deploy Docker Compose incluirá Jaeger (OTLP collector) para validar propagação de traces no quickstart local. Isso resolve LACUNA-05 — sem Jaeger, o AC INI-19 (propagação OTel) não pode ser verificado no quickstart. |
| P-06 | O `sme new` em Phase 1 NÃO registrará automaticamente client no Keycloak nem paths no Vault. O passo é manual e documentado. Resolve LACUNA-01 e LACUNA-02: o `shared-identity` em Phase 1 expõe apenas validação de tokens (resource server), sem Keycloak Admin API. |

### Lacunas críticas do spec (marcadas explicitamente)

| ID | Localização | Descrição | Impacto no plano |
|----|-------------|-----------|-----------------|
| LACUNA-01 | INI-US-08 / sme-cli | Não definido se `sme new` registra automaticamente client no Keycloak e paths no Vault. | Adotada Premissa P-06: passo manual em Phase 1. Escopo do CLI restrito a geração de código e renomeação de artefatos. |
| LACUNA-02 | INI-MS-03 / shared-identity | Não definido se `shared-identity` deve expor Keycloak Admin API. | Adotada Premissa P-06: Phase 1 apenas resource server / token validation. |
| LACUNA-03 | Geral / shared-secrets | Não definido: Spring Cloud Vault vs. Vault Agent Sidecar vs. integração manual para lease renewal. | Adotada Premissa P-03: Spring Cloud Vault. |
| LACUNA-04 | INI-US-05 / SBOM | Formato preferencial: CycloneDX ou SPDX? | Adotada Premissa P-04: CycloneDX via cyclonedx-maven-plugin. |
| LACUNA-05 | INI-US-07 / Quickstart | Reference-deploy inclui coletor OpenTelemetry? | Adotada Premissa P-05: Jaeger incluído no Docker Compose do quickstart local. |

---

## 3. Arquitetura Proposta

### 3.1 Visão de Componentes

```
secure-micro-energy-templates/               ← raiz do repositório
│
├── shared-controls/                         ← JAR: controles de segurança reutilizáveis
│   └── pom.xml (Spring Boot Starter, Jakarta Validation, Servlet Security)
│
├── shared-observability/                    ← JAR: logging JSON, Micrometer, OTel
│   └── pom.xml (Micrometer, OTel SDK, Logback/JSON encoder)
│
├── shared-identity/                         ← JAR: Spring Security OAuth2 Resource Server
│   └── pom.xml (Spring Security, spring-boot-starter-oauth2-resource-server)
│
├── shared-secrets/                          ← JAR: Spring Cloud Vault bootstrap
│   └── pom.xml (spring-cloud-vault-config, spring-cloud-starter-vault-config)
│
├── tmpl-rest-api/                           ← Template principal (Spring Boot WebFlux)
│   ├── src/
│   │   └── main/java/com/securemicro/tmpl/restapi/
│   │       ├── TmplRestApiApplication.java
│   │       ├── api/                         ← Controllers WebFlux
│   │       ├── security/                    ← Beans de segurança, filtros
│   │       ├── observability/               ← MDC propagation, audit trail
│   │       └── config/                      ← Spring @Configuration classes
│   ├── config/                              ← application.yml (sem secrets — refs ao Vault)
│   ├── security/                            ← documentação de controles deste template
│   ├── observability/                       ← configuração de exporters OTel / logging
│   ├── build/                               ← Dockerfile, scripts de build reproduzível
│   ├── deploy/                              ← docker-compose.yml (local), helm/ (referência)
│   ├── threat-model.md
│   └── control-mapping.md
│
├── tmpl-event-driven/                       ← Diretório reservado (Phase 2)
│   └── README.md (status: Phase 2 — planejado)
│
├── tmpl-batch-job/                          ← Diretório reservado (Phase 2)
│   └── README.md (status: Phase 2 — planejado)
│
├── sme-cli/                                 ← CLI Python (protótipo Phase 1)
│   ├── sme/
│   │   ├── __init__.py
│   │   ├── cli.py                           ← entry point (argparse ou Click)
│   │   ├── commands/new.py                  ← lógica de scaffold
│   │   └── templates.py                     ← registro de templates disponíveis
│   ├── pyproject.toml
│   └── README.md
│
├── docs/
│   ├── control-mappings/
│   │   └── tmpl-rest-api-controls.md
│   ├── threat-models/
│   │   └── tmpl-rest-api-threat-model.md    ← espelho do arquivo no template
│   └── quickstart/
│       └── tmpl-rest-api-quickstart.md
│
├── reference-deploy/
│   ├── docker-compose.yml                   ← Keycloak + Vault (dev) + tmpl-rest-api + Jaeger
│   ├── keycloak/
│   │   └── realm-export.json                ← realm pré-configurado com MFA obrigatório
│   └── vault/
│       └── vault-dev-init.sh                ← script de seed de paths no Vault (modo dev)
│
├── pom.xml                                  ← Multi-Module Maven POM raiz
├── .github/
│   └── workflows/
│       ├── ci.yml                           ← build + test + security gates
│       └── sbom.yml                         ← geração e validação de SBOM
└── .gitignore
```

**Nota sobre Maven Multi-Module:** O `pom.xml` raiz declara todos os módulos (`shared-*` e `tmpl-rest-api`) para build unificado. Os módulos `shared-*` são publicados como JARs locais (`mvn install`) e referenciados pelo `tmpl-rest-api` como dependências Maven.

### 3.2 Fluxo Principal

**Fluxo de requisição autenticada no tmpl-rest-api:**

```
Cliente HTTP
    │
    ▼  Bearer JWT
[WebFlux Router]
    │
    ▼
[Spring Security Filter Chain]
    │── JwtDecoder → valida assinatura (JWKS endpoint do Keycloak)
    │── verifica exp, aud, iss (via KEYCLOAK_ISSUER_URI)
    │── extrai roles → JwtAuthenticationToken
    │
    ▼
[MDC Propagation Filter]           ← correlation-id, subject, trace-id
    │
    ▼
[Controller WebFlux]
    │── autorização por @PreAuthorize / hasRole()
    │── lógica de negócio (placeholder no template)
    │── emite audit event (se ação de modificação)
    │
    ▼
[Logging Filter]                   ← emite log JSON estruturado no completion
    │
    ▼
[OTel Span Propagation]            ← W3C TraceContext para downstream
    │
    ▼
Resposta HTTP
```

**Fluxo de inicialização — resolução de secrets:**

```
Spring Boot startup
    │
    ▼
[Spring Cloud Vault BootstrapContext]
    │── conecta ao Vault via VAULT_ADDR + VAULT_TOKEN (ou AppRole)
    │── resolve KV v2 paths configurados
    │── injeta como PropertySource (disponível para @Value, @ConfigurationProperties)
    │── falha fast com mensagem clara se Vault inacessível
    │
    ▼
[shared-identity bootstrap]
    │── lê client-secret do Vault (nunca de env var ou application.yml)
    │── configura JwtDecoder com KEYCLOAK_ISSUER_URI
    │
    ▼
Contexto Spring disponível → servidor aceita requisições
```

**Fluxo sme-cli:**

```
$ sme new tmpl-rest-api meu-servico
    │
    ▼
[cli.py] valida: template existe? diretório destino já existe?
    │
    ▼
[commands/new.py]
    │── copia conteúdo de tmpl-rest-api → ./meu-servico/
    │── substitui com.securemicro.tmpl.restapi → com.securemicro.meu-servico
    │── atualiza artifactId e <name> no pom.xml
    │── exibe instrução manual: configurar Keycloak client + Vault paths (documentado)
    │
    ▼
Projeto compilável em ./meu-servico/
```

### 3.3 Decisões Arquiteturais (com trade-offs)

| Decisão | Escolha | Trade-off aceito | Alternativa rejeitada |
|---------|---------|-----------------|----------------------|
| Stack do serviço | Spring Boot 3.x + WebFlux (reativo) | Curva de aprendizado maior que Spring MVC | Spring MVC — rejeitado porque PRD seção 5.2 especifica WebFlux explicitamente |
| Integração com Vault | Spring Cloud Vault (ConfigData) | Acoplamento ao ecossistema Spring Cloud | Vault Agent Sidecar — exige Kubernetes; fora do escopo Phase 1 |
| Integração com Keycloak | spring-boot-starter-oauth2-resource-server (JWKS) | Sem suporte a Admin API no Phase 1 | Keycloak Admin Client — rejeitado por LACUNA-02; escopo Phase 2 |
| SBOM | CycloneDX Maven Plugin | SPDX tem menor suporte no ecossistema Maven em 2026 | SPDX — aceito pelo EO 14028, mas tooling Maven menos maduro |
| Build reproduzível | `mvn -Dproject.build.outputTimestamp=...` + Jib (Google Jib Maven Plugin) | Jib não suporta Dockerfile customizado diretamente | `docker build` padrão — não garante reprodutibilidade sem flag `--no-cache` + timestamps |
| OTel no quickstart | Jaeger (OTLP over HTTP, modo all-in-one) | Adiciona um container ao Docker Compose local | Sem coletor — impossibilita validação do AC INI-19 no quickstart |
| sme-cli language | Python 3.11+ | Time de energia pode não ter Python no PATH | Node.js — alternativa; rejeitado por Python ser mais onipresente no monorepo |
| Multi-module Maven | Sim, pom.xml raiz | Aumenta complexidade inicial de build | Repositórios separados — rejeitado porque PRD descreve estrutura mono-repo explicitamente |

---

## 4. Modelos de Dados

Esta feature não introduz banco de dados relacional. Os "dados" são estruturados em três camadas:

### 4.1 Secrets no Vault (KV v2)

| Path no Vault | Conteúdo | Consumidor |
|---------------|----------|-----------|
| `secret/tmpl-rest-api/keycloak` | `client-secret` | shared-identity |
| `secret/tmpl-rest-api/service` | `api-key` (placeholder) | tmpl-rest-api |
| `secret/tmpl-rest-api/db` | `username`, `password` (placeholder para extensão futura) | tmpl-rest-api |

Esses paths são documentados em `/tmpl-rest-api/config/vault-paths.md` e referenciados no `application.yml` via `spring.config.import=vault://`.

### 4.2 Realm Keycloak (realm-export.json)

Configurações do realm de referência:

| Atributo | Valor | Controle |
|----------|-------|---------|
| `otpPolicyType` | totp | MFA habilitado (INI-09, RN-02) |
| `browserFlow` | browser-with-otp | MFA obrigatório no browser flow |
| `clients[tmpl-rest-api].publicClient` | false | Client confidencial |
| `clients[tmpl-rest-api].secret` | `${VAULT_MANAGED}` — instrução, não valor real | RN-01 |
| `roles` | `ROLE_SERVICE_USER`, `ROLE_SERVICE_ADMIN` | Autorização por roles (INI-05) |

### 4.3 Log estruturado (schema JSON por evento)

**Evento de requisição:**
```
timestamp, level, correlation-id, trace-id, span-id,
http.method, http.path, http.status, duration_ms,
auth.subject (se autenticado), service.name, service.version
```

**Evento de audit trail:**
```
timestamp, event-type="AUDIT", correlation-id,
audit.subject, audit.action, audit.resource,
audit.result (SUCCESS|FAILURE), audit.reason (em falha)
```

Não há migration de schema — o logging é append-only para arquivos/stdout.

---

## 5. Contratos de API

O `tmpl-rest-api` é um template, não um serviço de negócio. Os endpoints são placeholders funcionais que demonstram os controles. A OpenAPI spec é gerada via `springdoc-openapi`.

### 5.1 Endpoints do template

| Método | Path | Auth | Role | Descrição |
|--------|------|------|------|-----------|
| GET | `/actuator/health` | Não | — | Health check (INI-08) |
| GET | `/actuator/health/liveness` | Não | — | Liveness probe (INI-20) |
| GET | `/actuator/health/readiness` | Não | — | Readiness probe (INI-20) |
| GET | `/actuator/prometheus` | Não* | — | Métricas Micrometer (INI-18) |
| GET | `/api/v1/resources` | Sim | `ROLE_SERVICE_USER` | Exemplo: leitura de recurso |
| POST | `/api/v1/resources` | Sim | `ROLE_SERVICE_ADMIN` | Exemplo: criação (gera audit trail) |
| GET | `/api/v1/hello` | Sim | `ROLE_SERVICE_USER` | Endpoint de smoke test |

*`/actuator/prometheus` pode ser protegido por network policy em produção; no template é aberto para facilitar o quickstart.

### 5.2 Códigos de erro e formato de resposta

| Cenário | HTTP | Body |
|---------|------|------|
| Token ausente ou inválido | 401 | `{"error": "unauthorized"}` — genérico, sem detalhe |
| Token válido, role insuficiente | 403 | `{"error": "forbidden"}` — genérico |
| Vault indisponível no startup | — | Serviço não inicia (fail fast) |
| Keycloak indisponível em runtime | 503 | `{"error": "service_unavailable"}` — sem detalhe interno |
| Erro interno não tratado | 500 | `{"error": "internal_error"}` — stack trace NUNCA exposto (RN-10) |
| Validação de input falha | 400 | `{"error": "bad_request", "details": [...]}` — apenas campos, sem stack |

### 5.3 Headers obrigatórios nas respostas

| Header | Valor | Controle |
|--------|-------|---------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | HSTS (shared-controls) |
| `X-Content-Type-Options` | `nosniff` | shared-controls |
| `X-Frame-Options` | `DENY` | shared-controls |
| `Content-Security-Policy` | `default-src 'none'` | shared-controls |
| `X-Correlation-ID` | valor propagado ou gerado | Observabilidade |

---

## 6. Componentes Afetados

O repositório está em estado inicial — não há código existente. Todos os componentes são criados do zero. A tabela abaixo descreve o que será criado e a responsabilidade de cada parte.

| Componente / Arquivo | Tipo | Impacto / Descrição |
|---------------------|------|---------------------|
| `pom.xml` (raiz) | Criação | Multi-module POM declarando shared-* e tmpl-rest-api; gerencia BOM do Spring Boot e Spring Cloud |
| `shared-controls/` | Criação | JAR com filtros de security headers, global exception handler (sem stack trace), validação de input |
| `shared-observability/` | Criação | JAR com auto-configuração: Logback + logstash-logback-encoder (JSON), Micrometer registry, OTel SDK auto-instrumentation |
| `shared-identity/` | Criação | JAR com configuração de resource server OAuth2, extrator de claims, utilitários de autorização por role |
| `shared-secrets/` | Criação | JAR com bootstrap Spring Cloud Vault, property source, mascaramento de secrets em logs |
| `tmpl-rest-api/src/` | Criação | Aplicação Spring Boot WebFlux completa com todos os controles integrados via shared-* |
| `tmpl-rest-api/config/application.yml` | Criação | Configuração sem secrets — todos os valores sensíveis referenciados via `spring.config.import=vault://` |
| `tmpl-rest-api/build/Dockerfile` | Criação | Multi-stage Dockerfile com Jib ou `docker build` com flags de reprodutibilidade |
| `tmpl-rest-api/threat-model.md` | Criação | Threat model STRIDE completo (INI-29) |
| `tmpl-rest-api/control-mapping.md` | Criação | Mapeamento de cada controle para cláusula federal (INI-27) |
| `reference-deploy/docker-compose.yml` | Criação | Keycloak + Vault (dev mode) + tmpl-rest-api + Jaeger — single command (INI-32) |
| `reference-deploy/keycloak/realm-export.json` | Criação | Realm pré-configurado com MFA obrigatório (INI-09) |
| `reference-deploy/vault/vault-dev-init.sh` | Criação | Script de seed dos paths KV v2 no Vault em modo dev (INI-33) |
| `docs/quickstart/tmpl-rest-api-quickstart.md` | Criação | Guia clone → rodando em menos de 30 minutos (INI-31) |
| `docs/control-mappings/tmpl-rest-api-controls.md` | Criação | Mapeamento consolidado EO 14028 / NIST 800-53 / CISA |
| `sme-cli/sme/cli.py` | Criação | Protótipo CLI Python: `sme new`, `sme --version` |
| `sme-cli/sme/commands/new.py` | Criação | Lógica de scaffold: cópia + renomeação de pacotes + atualização de pom.xml |
| `.github/workflows/ci.yml` | Criação | Pipeline CI: build, test, secret scan (trufflehog), CVE scan (Trivy), verificação de threat-model.md |
| `tmpl-event-driven/README.md` | Criação | Diretório reservado com status Phase 2 (INI-02) |
| `tmpl-batch-job/README.md` | Criação | Diretório reservado com status Phase 2 (INI-02) |

---

## 7. Dependências Externas

### 7.1 Bibliotecas Java (Maven)

| Biblioteca | Versão referência | Uso | Justificativa |
|-----------|-------------------|-----|---------------|
| Spring Boot | 3.3.x | Framework base | Padrão do monorepo |
| Spring WebFlux | (via Spring Boot BOM) | Reactive HTTP server | Especificado no PRD 5.2 |
| spring-boot-starter-oauth2-resource-server | (via Spring Boot BOM) | JWT validation + Keycloak OIDC | shared-identity |
| spring-cloud-vault-config | 4.x | Vault integration | shared-secrets (Premissa P-03) |
| micrometer-registry-prometheus | (via Spring Boot BOM) | Métricas Prometheus | shared-observability |
| opentelemetry-spring-boot-starter | 2.x | Tracing OTel | shared-observability |
| logstash-logback-encoder | 7.x | JSON logging | shared-observability |
| cyclonedx-maven-plugin | 2.x | SBOM CycloneDX | Premissa P-04 |
| spring-boot-starter-actuator | (via Spring Boot BOM) | Health probes, métricas | tmpl-rest-api |
| springdoc-openapi-starter-webflux-ui | 2.x | OpenAPI docs | tmpl-rest-api |
| spring-boot-starter-validation | (via Spring Boot BOM) | Input validation | shared-controls |

### 7.2 Infraestrutura externa (Docker Compose — ambiente local)

| Serviço | Imagem referência | Uso | Credenciais |
|---------|-------------------|-----|-------------|
| Keycloak | `quay.io/keycloak/keycloak:24.x` | IDP OIDC com MFA | Admin password via Docker secret — documentada como DEV ONLY |
| HashiCorp Vault | `hashicorp/vault:1.17.x` | Secrets management (modo dev local) | Token `root` — DEV ONLY, documentado |
| Jaeger | `jaegertracing/all-in-one:1.57` | OTel collector + UI | Sem credenciais no modo dev |

### 7.3 Ferramentas de CI

| Ferramenta | Uso | Observação |
|-----------|-----|------------|
| trufflehog ou git-secrets | Varredura de secrets no repositório (INI-15) | Executado em cada PR |
| Trivy | Scan de CVE nas dependências e imagem Docker (INI-25) | Bloqueia build em CVE crítica ou alta |
| GitHub Actions | Plataforma de CI | Repositório está em GitHub (git config indica GitHub) |

### 7.4 Credenciais e infra necessárias para uso do template

Não há credenciais necessárias para o build do repositório em si. Para o quickstart local, as únicas credenciais são as geradas pelo próprio Docker Compose (Keycloak admin, Vault root token em modo dev) — todas documentadas como DEV ONLY no guia de quickstart.

---

## 8. Áreas Sensíveis

- **Autenticação/autorização/sessão**: SIM. O `tmpl-rest-api` implementa autenticação OIDC via Keycloak com validação de JWT (assinatura, expiração, audience). O `shared-identity` contém toda a lógica de configuração do resource server Spring Security. Arquivos envolvidos: `shared-identity/src/`, `tmpl-rest-api/src/main/java/.../security/`, `tmpl-rest-api/config/application.yml`, `reference-deploy/keycloak/realm-export.json`.

- **Pagamento/faturamento/cálculo financeiro real**: NÃO. O template é de propósito geral para o setor de energia; não há lógica financeira.

- **Dados pessoais/sensíveis (PII, saúde, financeiro)**: SIM (indireto). O JWT contém `subject` (identidade do usuário) que é logado no audit trail. O mascaramento de secrets nos logs implementado pelo `shared-secrets` e `shared-observability` protege valores de segredo. O `subject` do token não é considerado secret mas é dado de identidade — o audit trail deve reter apenas o identificador de sujeito, não dados pessoais adicionais. Arquivos envolvidos: `shared-secrets/src/` (mascaramento), `shared-observability/src/` (log estruturado), `tmpl-rest-api/src/.../observability/`.

- **Migration de dados em tabela com produção**: NÃO. Esta feature não introduz banco de dados relacional.

- **Lógica regulatória/fiscal/compliance**: SIM. Todo o repositório é orientado a compliance federal (EO 14028, NIST SP 800-53, CISA). O `control-mapping.md` e o `threat-model.md` são documentos de compliance que devem ser precisos. Se incorretos, criam exposição de responsabilidade para adotantes. Arquivos envolvidos: `tmpl-rest-api/control-mapping.md`, `tmpl-rest-api/threat-model.md`, `docs/control-mappings/`.

- **Endpoint público sem autenticação prévia**: SIM. `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` e `/actuator/prometheus` são intencionalmente públicos. Embora seja o comportamento correto para probes, esses endpoints devem ser verificados para não vazar informações sensíveis (versões de biblioteca, detalhes de ambiente). Arquivos envolvidos: `tmpl-rest-api/config/application.yml` (configuração do actuator), `shared-controls/src/` (security headers nesses endpoints).

- **Criptografia/manuseio de chaves**: SIM. O JwtDecoder do Spring Security valida assinatura JWT usando JWKS públicos do Keycloak. As chaves privadas nunca tocam o serviço (assimetria RSA). O `shared-secrets` manuseia o token de AppRole do Vault para autenticação — esse token deve ter lease curto. Arquivos envolvidos: `shared-identity/src/` (JwtDecoder), `shared-secrets/src/` (Vault auth), `reference-deploy/vault/vault-dev-init.sh`.

- **Integração externa nova com terceiro**: SIM. Duas integrações externas novas: (1) Keycloak via OIDC/JWKS — `shared-identity`; (2) HashiCorp Vault via API REST — `shared-secrets`. Ambas são integrações de segurança críticas. Arquivos envolvidos: `shared-identity/src/`, `shared-secrets/src/`, `reference-deploy/docker-compose.yml`.

---

## 9. Riscos e Mitigações

| Prioridade | Risco | Probabilidade | Impacto | Mitigação |
|-----------|-------|--------------|---------|----------|
| 1 | **Spring Cloud Vault não suporta lease renewal transparente para KV v2** — KV v2 não usa leases; apenas credenciais dinâmicas (database, AWS) têm lease. Secrets estáticos exigem rotação por reescrita no Vault. | Alta | Alto | Documentar explicitamente que KV v2 requer rotação ativa (não lease renewal automático). Para Phase 1 o AC INI-13 deve ser reescrito: "serviço busca novamente o secret sem reinicialização via Spring Cloud Vault refresh endpoint". Marcar como requer revisão de AC com o product owner antes da implementação. |
| 2 | **Build reproduzível com Jib** — Jib gera imagens sem Dockerfile, o que pode conflitar com a necessidade de customizações de segurança (usuário não-root, read-only filesystem). | Média | Médio | Usar Jib com configuração explícita de usuário não-root e avaliar. Se insuficiente, usar `docker build` com `SOURCE_DATE_EPOCH` fixo para reprodutibilidade. Decisão final na task de implementação do build. |
| 3 | **Tempo do quickstart > 30 minutos** — pull de 4 imagens Docker (Keycloak, Vault, Jaeger, app) em rede lenta pode exceder o SLA. | Média | Médio | Documentar pré-requisitos (pull das imagens com `docker compose pull` antes do quickstart cronometrado). Validar o quickstart em máquina limpa antes da entrega. |
| 4 | **Drift de control-mapping.md** — se o código mudar sem atualizar o mapeamento, a rastreabilidade bidirecional (INI-30) se rompe. | Alta | Alto | Gate de CI: script que verifica que cada ID de controle listado no `control-mapping.md` tem um comentário `// CONTROL: <ID>` correspondente no código. Implementado como step no `ci.yml`. |
| 5 | **sme-cli em Python pode não estar disponível em todas as máquinas de energia** | Média | Baixo | Documentar Python 3.11+ como pré-requisito. Considerar distribuição como binário único via PyInstaller em Phase 2 se houver demanda. |
| 6 | **Keycloak realm-export.json fica desatualizado** com novas versões do Keycloak que mudam o schema do export. | Baixa | Médio | Versionar a imagem do Keycloak no Docker Compose e documentar o processo de atualização do realm. |
| 7 | **CVE em dependência transitória bloqueia o build sem solução imediata** | Média | Médio | Configurar Trivy com política de supressão documentada para falsos positivos; manter mecanismo de override com justificativa versionada. |

---

## 10. Critérios de Aceite Técnicos

Os critérios abaixo determinam que o plano foi bem executado:

1. **Estrutura completa**: `git clone` seguido de `tree -d -L 2` reproduz exatamente a estrutura definida na seção 3.1, incluindo os diretórios reservados de Phase 2 com README.

2. **Build limpo**: `mvn clean verify` na raiz do repositório compila todos os módulos, passa todos os testes unitários e gera SBOM CycloneDX sem warnings de CVE crítica ou alta.

3. **Autenticação funcional**: requisição sem token retorna HTTP 401; com token válido e role correta retorna HTTP 200; com token válido e role errada retorna HTTP 403. Verificado via teste de integração com Keycloak testcontainer ou WireMock.

4. **Zero secrets no repositório**: `trufflehog filesystem .` e `git-secrets --scan` retornam zero findings no repositório completo.

5. **Quickstart em menos de 30 minutos**: sequência `docker compose up -d` → esperar healthchecks → obter token JWT do Keycloak → `curl` no endpoint autenticado → ver log JSON no stdout → `curl /actuator/prometheus` — completada em menos de 30 minutos em máquina com Docker instalado e imagens já baixadas.

6. **Audit trail**: `POST /api/v1/resources` com token válido gera linha de log com `event-type=AUDIT` contendo `subject`, `action`, `timestamp` e `result`.

7. **Métricas presentes**: `curl /actuator/prometheus` retorna métricas com prefixos `http_server_requests`, `jvm_`, e pelo menos uma métrica de latência por percentil.

8. **OTel funcionando**: Jaeger UI em `http://localhost:16686` exibe traces gerados pelo serviço após requisições.

9. **Threat model e control mapping presentes e não-vazios**: CI gate falha se qualquer um dos dois arquivos estiver ausente ou vazio.

10. **sme-cli funcional**: `sme new tmpl-rest-api meu-servico` cria diretório `meu-servico`, o pacote Java renomeado para `com.securemicro.meu-servico` e `mvn package` no projeto gerado compila sem erros.

11. **Rastreabilidade bidirecional**: cada controle listado no `control-mapping.md` possui referência identificável no código ou configuração do template (comentário `// CONTROL: <ID>` ou equivalente em YAML).

12. **Vault fail-fast**: iniciar o serviço com Vault inacessível resulta em falha imediata com mensagem de log clara — o processo não sobe parcialmente.
