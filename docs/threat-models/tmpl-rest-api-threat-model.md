# Threat Model — tmpl-rest-api

**Template:** tmpl-rest-api  
**Versão:** 1.0.0  
**Data:** 2026-05-17  
**Status:** Draft  
**Requisito:** INI-29 (INI-US-06)  
**Autor:** SecureMicro-Energy-Templates Platform Team  
**Revisores:** Revisor AppSec (cargo), Engenheiro de Energia (operador)  
**Frameworks de referência:** EO 14028 (Zero Trust Architecture), NIST SP 800-53 rev5, CISA Secure by Design

---

## Sumário

1. [Escopo e Premissas](#1-escopo-e-premissas)
2. [Atores](#2-atores)
3. [Trust Boundaries](#3-trust-boundaries)
4. [Fluxo de Dados](#4-fluxo-de-dados)
5. [Análise STRIDE por Componente](#5-análise-stride-por-componente)
6. [Ameaças Fora do Escopo (Out of Scope)](#6-ameaças-fora-do-escopo-out-of-scope)
7. [Resumo de Controles Implementados](#7-resumo-de-controles-implementados)
8. [Referências](#8-referências)

---

## 1. Escopo e Premissas

### 1.1 Componentes no escopo

Este threat model cobre os seguintes componentes do `tmpl-rest-api` e suas integrações diretas:

| Componente | Descrição |
|-----------|-----------|
| `tmpl-rest-api` | Serviço Spring Boot WebFlux que expõe endpoints REST protegidos por JWT |
| `shared-identity` | Módulo de validação de JWT via Keycloak OIDC (resource server) |
| `shared-controls` | Módulo com filtros de security headers e global exception handler |
| `shared-observability` | Módulo de logging estruturado JSON, Micrometer, OpenTelemetry |
| `shared-secrets` | Módulo de integração com HashiCorp Vault via Spring Cloud Vault |
| Keycloak (externo) | Identity Provider OIDC — emite e valida tokens JWT |
| HashiCorp Vault (externo) | Secrets manager — KV v2 com paths `secret/tmpl-rest-api/*` |
| Serviços downstream (externo) | Serviços REST chamados pelo template como cliente HTTP |
| Kubernetes / infra | Orquestrador de containers onde o pod do serviço executa |

### 1.2 Premissas arquiteturais

- HTTPS é gerenciado pelo ingress/load balancer externo. O serviço em si não termina TLS.
- O Keycloak realm de referência tem MFA obrigatório habilitado (TOTP — INI-09).
- O Vault opera em modo production (não dev) em ambientes não-locais.
- O serviço não armazena dados de negócio persistentes — é um template de demonstração.
- Credenciais do Vault (AppRole ou Token) têm TTL curto e são renováveis.
- A comunicação serviço → Keycloak e serviço → Vault ocorre dentro de uma rede privada controlada.

---

## 2. Atores

### 2.1 Atores legítimos

| Ator | Papel | Nível de Confiança | Acesso típico |
|------|-------|---------------------|---------------|
| **Engenheiro de Energia (operador)** | Desenvolve, configura e faz deploy de serviços baseados no template | Alto (operador autenticado) | Acesso ao código-fonte, configurações, logs de aplicação, CLI sme |
| **Revisor AppSec** | Audita controles de segurança implementados pelo template, verifica rastreabilidade federal | Médio-alto (acesso de leitura) | Leitura do código, control-mapping.md, threat-model.md, relatórios de SBOM e CVE |
| **Usuário de serviço autenticado** | Consome os endpoints do serviço com token JWT válido emitido pelo Keycloak | Médio (autenticado com role) | Endpoints `/api/v1/resources` (GET com ROLE_SERVICE_USER) e `/api/v1/hello` |
| **Administrador do serviço** | Opera endpoints de modificação com role elevada | Médio-alto (autenticado com role admin) | Endpoint `/api/v1/resources` (POST com ROLE_SERVICE_ADMIN) |
| **sme-cli** | Agente automatizado que instancia e configura templates | Médio (executa localmente) | Acesso ao sistema de arquivos local para scaffold de projeto |

### 2.2 Ator adversário

| Ator | Motivação | Capacidade estimada | Vetor de ataque esperado |
|------|-----------|---------------------|--------------------------|
| **Atacante externo** | Exfiltração de dados, comprometimento de credenciais, interrupção de serviço, pivô para sistemas internos de energia | Alta (nação-estado ou grupo organizado dado o setor de energia) | Exploração de endpoints públicos, roubo de tokens JWT, injeção em payloads, ataques de força bruta ao Keycloak, reconhecimento via actuator |

---

## 3. Trust Boundaries

As trust boundaries definem os limites onde o nível de confiança muda entre componentes. Cruzar uma boundary exige autenticação, autorização ou verificação de integridade explícita.

### TB-01: Cliente Externo → tmpl-rest-api

**Descrição:** Fronteira entre qualquer cliente HTTP externo (usuário, sistema, atacante) e o serviço REST API.

**Controles aplicados:**
- Bearer JWT obrigatório em todos os endpoints de negócio (exceto `/actuator/health*` e `/actuator/prometheus`) — CONTROL: INI-04
- Validação completa do JWT: assinatura RSA via JWKS do Keycloak, `exp`, `iss`, `aud` — CONTROL: INI-07
- Autorização por role via `@PreAuthorize` e `@EnableReactiveMethodSecurity` — CONTROL: INI-05
- Security headers HTTP em todas as respostas: `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy`, `HSTS` — CONTROL: INI-07 (shared-controls)
- Stack traces nunca expostos no corpo de resposta — CONTROL: RN-10
- Correlation-ID gerado ou propagado para rastreabilidade — CONTROL: INI-16

**Ameaça cruzando esta boundary:** Spoofing (token forjado), Tampering (payload manipulado), DoS (flood de requisições), Information Disclosure (respostas verbosas).

---

### TB-02: tmpl-rest-api → Keycloak (OIDC/JWKS)

**Descrição:** Fronteira entre o serviço e o Identity Provider Keycloak. O serviço atua como resource server que valida tokens consultando o JWKS endpoint do Keycloak.

**Controles aplicados:**
- Issuer URI configurada exclusivamente via variável de ambiente `KEYCLOAK_ISSUER_URI` — nunca hardcoded — CONTROL: INI-06
- O serviço valida a assinatura JWT usando as chaves públicas JWKS do Keycloak (verificação criptográfica, sem segredos compartilhados) — CONTROL: INI-07
- Se Keycloak estiver indisponível em runtime, o serviço retorna HTTP 503 sem expor detalhes — CONTROL: RN-10
- Credenciais do client Keycloak (client-secret) obtidas do Vault, não de env vars — CONTROL: RN-01, INI-10

**Ameaça cruzando esta boundary:** Spoofing (Keycloak falso fornecendo JWKS maliciosos), Tampering (chaves JWKS substituídas em trânsito), Repudiation (eventos de autenticação não auditados no Keycloak), Information Disclosure (tokens capturados em trânsito), Denial of Service (Keycloak indisponível bloqueia autenticação), Elevation of Privilege (token forjado ou realm_access.roles adulterado eleva privilégios).

---

### TB-03: tmpl-rest-api → HashiCorp Vault (KV v2)

**Descrição:** Fronteira entre o serviço e o Vault. O serviço autentica no Vault no bootstrap para obter todos os segredos necessários (client-secret do Keycloak, credenciais de banco, API keys).

**Controles aplicados:**
- Bootstrap via Spring Cloud Vault com autenticação AppRole (role-id + secret-id com TTL curto) — CONTROL: RN-01, INI-12
- Secrets nunca expostos em logs — mascaramento automático via `shared-secrets` — CONTROL: INI-11
- Serviço falha fast (`fail-fast: true`) se Vault estiver inacessível no startup — CONTROL: INI-12
- Paths documentados: `secret/tmpl-rest-api/keycloak`, `secret/tmpl-rest-api/service`, `secret/tmpl-rest-api/db` — CONTROL: INI-14
- Nenhum segredo em `application.yml`, env vars ou arquivos versionados — CONTROL: RN-01

**Ameaça cruzando esta boundary:** Spoofing (Vault falso), Information Disclosure (secrets expostos em logs), Repudiation (acesso a secrets sem auditoria), Elevation of Privilege (acesso a paths além do necessário).

---

### TB-04: tmpl-rest-api → Serviços Downstream

**Descrição:** Fronteira entre o serviço atuando como cliente HTTP e qualquer serviço downstream que ele chama. Em produção, inclui outros microserviços, APIs internas ou sistemas legados de energia.

**Controles aplicados:**
- Propagação de W3C TraceContext (trace-id, span-id) para correlação de logs distribuídos — CONTROL: INI-19, INI-21
- Correlation-ID propagado no header `X-Correlation-ID` — CONTROL: INI-16
- Audit trail gerado para chamadas que resultam em modificações de dados — CONTROL: INI-17
- Graceful degradation: se o coletor OTel estiver indisponível, o serviço não falha — CONTROL: INI-21

**Ameaça cruzando esta boundary:** Tampering (manipulação de payload em trânsito), Information Disclosure (dados sensíveis propagados sem controle), Repudiation (chamadas sem rastreabilidade).

---

### TB-05: Kubernetes / Infra → Pod do tmpl-rest-api

**Descrição:** Fronteira entre a infraestrutura de orquestração (Kubernetes) e o container do serviço. Inclui injeção de variáveis de ambiente, montagem de volumes, permissões de rede e ciclo de vida do pod.

**Controles aplicados:**
- Liveness probe em `/actuator/health/liveness` — Kubernetes reinicia o pod se falhar — CONTROL: INI-20
- Readiness probe em `/actuator/health/readiness` — Kubernetes remove o pod do load balancer se não-pronto — CONTROL: INI-20
- Health endpoint `/actuator/health` acessível sem autenticação para probes de infra — CONTROL: INI-08
- Métricas Micrometer em `/actuator/prometheus` para coleta pelo Prometheus — CONTROL: INI-18
- Build de container com usuário não-root (Dockerfile/Jib) — CONTROL: RN-09
- SBOM CycloneDX gerado em cada build e associado à imagem — CONTROL: INI-22, INI-23
- Nenhuma variável de ambiente contém segredos — env vars são apenas configurações não-sensíveis — CONTROL: RN-01

**Ameaça cruzando esta boundary:** Elevation of Privilege (processo escapando do container), Information Disclosure (env vars com segredos inspecionados), DoS (pod consumindo recursos sem limite), Tampering (imagem substituída sem verificação de integridade).

---

## 4. Fluxo de Dados

### 4.1 Diagrama de fluxo principal — Requisição autenticada

```
+------------------+        +------------------+        +------------------+
|  Cliente Externo |        |  tmpl-rest-api   |        |    Keycloak      |
|  (Usuário/API)   |        |  (Spring WebFlux)|        |  (IDP OIDC)      |
+--------+---------+        +--------+---------+        +--------+---------+
         |                           |                           |
         |  [1] HTTP Request         |                           |
         |  Bearer: <JWT>            |                           |
         |  ══════════════════════> TB-01                        |
         |                           |                           |
         |                           |  [2] JWKS lookup          |
         |                           |  GET /.well-known/jwks   |
         |                           |  ═══════════════════════> TB-02
         |                           |                           |
         |                           |  [3] Public Keys (JWKS)  |
         |                           |  <═══════════════════════|
         |                           |                           |
         |                           |  [4] Validate: sig, exp, |
         |                           |  iss, aud               |
         |                           |  (local, sem round-trip) |
         |                           |                           |
         |                           |  [5] @PreAuthorize       |
         |                           |  verifica role do token  |
         |                           |                           |
         |  [6] HTTP Response        |                           |
         |  200 / 401 / 403 / 50x   |                           |
         |  <══════════════════════ TB-01                        |
         |                           |                           |
```

### 4.2 Diagrama de fluxo — Bootstrap de secrets (startup)

```
+------------------+        +------------------+        +------------------+
|  Spring Boot     |        | HashiCorp Vault  |        |    Keycloak      |
|  (startup)       |        |  (KV v2)         |        |  (em runtime)    |
+--------+---------+        +--------+---------+        +--------+---------+
         |                           |                           |
         |  [1] AppRole auth         |                           |
         |  role-id + secret-id      |                           |
         |  ═══════════════════════> TB-03                       |
         |                           |                           |
         |  [2] Vault Token (TTL)    |                           |
         |  <═══════════════════════|                           |
         |                           |                           |
         |  [3] GET secret/tmpl-rest-api/keycloak               |
         |  ═══════════════════════> TB-03                       |
         |                           |                           |
         |  [4] client-secret value  |                           |
         |  <═══════════════════════|                           |
         |                           |                           |
         |  [5] Configura JwtDecoder |                           |
         |  com KEYCLOAK_ISSUER_URI  |                           |
         |  e client-secret do Vault |                           |
         |  ══════════════════════════════════════════════════> TB-02
         |                           |                           |
         |  [6] OIDC Discovery OK    |                           |
         |  <══════════════════════════════════════════════════|
         |                           |                           |
         |  [7] Serviço pronto       |                           |
         |  (aceita requisições)     |                           |
```

### 4.3 Diagrama de fluxo — Audit trail (ação de modificação)

```
+------------------+        +------------------+        +------------------+
|  Cliente Admin   |        |  tmpl-rest-api   |        |  Serviço         |
|  (ROLE_ADMIN)    |        |                  |        |  Downstream      |
+--------+---------+        +--------+---------+        +--------+---------+
         |                           |                           |
         |  POST /api/v1/resources   |                           |
         |  Bearer: <JWT admin>      |                           |
         |  ══════════════════════> TB-01                        |
         |                           |                           |
         |                           |  AuditTrailService.emit()|
         |                           |  {subject, action,       |
         |                           |   resource, timestamp,   |
         |                           |   result=PENDING}        |
         |                           |  (CONTROL: INI-17)       |
         |                           |                           |
         |                           |  [call downstream]       |
         |                           |  X-Correlation-ID        |
         |                           |  W3C TraceContext        |
         |                           |  ═══════════════════════> TB-04
         |                           |                           |
         |                           |  [response]              |
         |                           |  <═══════════════════════|
         |                           |                           |
         |                           |  AuditTrailService.emit()|
         |                           |  {result=SUCCESS|FAILURE}|
         |                           |                           |
         |  HTTP 201 / 50x           |                           |
         |  <══════════════════════ TB-01                        |
```

---

## 5. Análise STRIDE por Componente

A análise STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) é aplicada a cada componente e trust boundary. Para cada ameaça identificada, são listadas as mitigações implementadas e os CONTROLs correspondentes.

---

### 5.1 STRIDE — TB-01: Cliente Externo → tmpl-rest-api

#### S — Spoofing (Falsificação de identidade)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Token JWT forjado | Atacante cria JWT com claims falsos sem ter a chave privada do Keycloak | Validação de assinatura RSA via JWKS público do Keycloak. Tokens sem assinatura válida são rejeitados com HTTP 401 | INI-07 |
| Replay de token expirado | Atacante captura e reenvia token expirado | Verificação do campo `exp` no JWT. Tokens expirados retornam HTTP 401 imediatamente | INI-07 |
| Token com audience errado | Token emitido para outro serviço é apresentado ao tmpl-rest-api | Verificação do campo `aud` no JWT. Tokens com audience divergente são rejeitados | INI-07 |
| Spoofing de correlation-id | Cliente forja o header `X-Correlation-ID` para confundir logs | Correlation-ID externo é aceito mas marcado como `external=true` nos logs; o serviço pode gerar seu próprio | INI-16 |

#### T — Tampering (Adulteração)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Manipulação do payload JWT | Atacante altera claims do JWT (ex: role) sem invalidar a assinatura | Impossível sem a chave privada do Keycloak. A validação da assinatura detecta qualquer alteração no payload | INI-07 |
| Injeção em body da requisição | Atacante envia payload malicioso (JSON injection, oversized payload) | `shared-controls`: validação de input via Bean Validation (`@Valid`), limite de tamanho no WebFlux | INI-04 |
| Manipulação de headers HTTP | Atacante injeta headers para manipular comportamento do serviço | SecurityHeadersFilter sobrescreve headers de segurança na resposta, independente do que o cliente enviar | INI-07 |

#### R — Repudiation (Negação)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Negar ação de modificação | Usuário nega ter feito POST que modificou dados | AuditTrailService emite evento com `subject` extraído do JWT, `action`, `timestamp` e `result`. Logs são append-only | INI-17 |
| Negar acesso ao serviço | Atacante nega ter acessado o endpoint | Log estruturado registra `auth.subject`, método HTTP, path e timestamp em cada requisição autenticada | INI-16 |

#### I — Information Disclosure (Divulgação de informações)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Stack trace em resposta de erro | Erro interno expõe stack trace com detalhes do framework/versão | `GlobalExceptionHandler` captura todas as exceções e retorna body genérico sem stack trace | RN-10 |
| Versão do framework em headers | Header `X-Powered-By` ou `Server` expõe versão do Spring | SecurityHeadersFilter remove/sobrescreve esses headers | INI-07 |
| Informação de timing em 401 vs 403 | Tempo de resposta diferente entre token inválido e role insuficiente vaza informação | Ambos retornam imediatamente após validação do JWT; latência uniforme | INI-07 |
| Dados sensíveis em logs de requisição | Subject do token ou dados do body expostos em logs | Log registra apenas `auth.subject` (identificador, não dados pessoais adicionais) e metadados HTTP | INI-16 |

#### D — Denial of Service (Negação de serviço)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Flood de requisições não autenticadas | Atacante envia milhares de requisições sem token para esgotar recursos | Rate limiting delegado ao ingress/API gateway (fora do escopo do serviço). Liveness/readiness probes permitem que Kubernetes isole o pod degradado | INI-20 |
| Payload oversized | Atacante envia body de 100MB para esgotar memória | Limite de tamanho configurado no WebFlux (`spring.webflux.codec.max-in-memory-size`) | INI-04 |
| JWKS unavailability | Keycloak indisponível impede validação de tokens | Cache de JWKS em memória (Spring Security) permite validar tokens por um período sem JWKS disponível | INI-07 |

#### E — Elevation of Privilege (Elevação de privilégio)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Acesso a endpoint admin com role user | Usuário com ROLE_SERVICE_USER tenta acessar POST /api/v1/resources | `@PreAuthorize("hasRole('SERVICE_ADMIN')")` rejeita com HTTP 403 | INI-05 |
| Acesso a endpoint protegido sem autenticação | Requisição sem token tenta acessar endpoint de negócio | Spring Security nega por default toda requisição não autenticada a endpoints protegidos (HTTP 401) | INI-04 |
| Manipulação de roles no JWT | Atacante tenta incluir role adicional em token | Impossível sem comprometer a chave privada do Keycloak (ver Spoofing T-01) | INI-07 |

---

### 5.2 STRIDE — TB-02: tmpl-rest-api → Keycloak

#### S — Spoofing

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Keycloak falso via DNS poisoning | Atacante redireciona JWKS lookup para servidor controlado por ele | TLS obrigatório na comunicação com Keycloak (gerenciado pelo ingress). `KEYCLOAK_ISSUER_URI` validada na inicialização | INI-06 |
| Adulteração do issuer URI | Configuração incorreta aponta para Keycloak não-confiável | Issuer URI nunca hardcoded — configurada via env var com validação no startup. CI escaneia segredos hardcoded | INI-06, INI-11 |

#### T — Tampering

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Substituição de chaves JWKS | MITM substitui chaves públicas retornadas pelo Keycloak | TLS garante integridade do transporte. Cache de JWKS reduz surface de ataque | INI-07 |

#### I — Information Disclosure

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Client-secret do Keycloak em logs | Spring loga configuração do resource server com client-secret | `shared-secrets` ativa mascaramento de valores com padrões de secret em logs. Client-secret vem do Vault, não de application.yml | RN-01, INI-10 |
| Client-secret hardcoded em application.yml | Desenvolvedor comete client-secret no repositório | CI executa varredura de segredos (trufflehog/git-secrets). `application.yml` usa referências Vault, não valores diretos | INI-11, INI-15 |

#### D — Denial of Service

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Keycloak indisponível em runtime | IDP fora do ar impede qualquer autenticação | Cache de JWKS mantém validação funcionando temporariamente. Serviço retorna HTTP 503 com body genérico enquanto IDP inacessível | INI-07, RN-10 |

#### R — Repudiation (Negação)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Negar tentativas de autenticação | Atacante nega ter tentado autenticar-se; ausência de logs no Keycloak torna impossível auditar eventos de login | Keycloak deve ter audit logging habilitado no realm (event listeners: `jboss-logging`, `sysout`). Eventos de login, falha de autenticação e emissão de token registrados de forma auditável e imutável no aggregador de logs | INI-17, NIST AU-2 |
| Ausência de rastreabilidade de token emitido | Não é possível correlacionar um token JWT em uso com o evento de emissão no Keycloak | `jti` (JWT ID) presente no token permite cruzar evento de emissão no Keycloak com uso do token no serviço via logs estruturados | INI-16 |

**Risco residual:** O logging de eventos do Keycloak é responsabilidade do operador que configura o realm. O template documenta o requisito mas não pode impor a configuração do IDP externo.

#### E — Elevation of Privilege (Elevação de privilégio)

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Token com `realm_access.roles` adulterado | Atacante modifica o claim de roles no payload do JWT para incluir `ROLE_SERVICE_ADMIN` sem ter a permissão | Impossível sem comprometer a chave privada do Keycloak. A validação de assinatura RSA (INI-07) detecta qualquer alteração no payload do token | INI-07 |
| Token forjado com roles elevadas | Atacante cria JWT sintético com claims de admin apontando para o realm correto | A validação do JWKS garante que apenas tokens assinados pela chave privada do Keycloak são aceitos. Tokens forjados falham na verificação de assinatura (HTTP 401) | INI-07 |
| Comprometimento de client-secret eleva acesso | Client-secret vazado permite que atacante obtenha tokens válidos em nome de usuários | Client-secret obtido exclusivamente do Vault (não de env vars ou application.yml). TTL curto e renovação automática limitam janela de exposição | RN-01, INI-10 |

**Risco residual:** Um comprometimento total da chave privada RSA do Keycloak permitiria forjar tokens com qualquer role. Esse cenário é tratado como OOS-05 (comprometimento do Keycloak em si é responsabilidade do operador).

---

### 5.3 STRIDE — TB-03: tmpl-rest-api → HashiCorp Vault

#### S — Spoofing

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Vault falso no bootstrap | Atacante responde no endereço do Vault com secrets falsos | TLS na comunicação com Vault. `VAULT_ADDR` configurado via env var; não hardcoded. Fail-fast se Vault inacessível | RN-01, INI-12 |

#### T — Tampering

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Adulteração de secrets no trânsito | MITM modifica valores de secrets retornados pelo Vault | TLS obrigatório na comunicação com Vault. Valores validados pela aplicação que os consome | RN-01 |

#### R — Repudiation

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Acesso não auditado a secrets | Leitura de secrets sem registro | Vault audit log registra todos os acessos via API. Log estruturado do serviço registra eventos de bootstrap | INI-17, NIST AU-9 |

#### I — Information Disclosure

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Secrets em variáveis de ambiente | Operador expõe secrets via env vars como workaround | `shared-secrets` é o único ponto de acesso a secrets. `application.yml` não contém valores diretos | RN-01 |
| Secrets em logs de startup | Spring Cloud Vault loga valores de properties resolvidas | Mascaramento automático de valores com padrões de secret nos logs via `shared-secrets` | INI-11 |
| Acesso ao Vault além do escopo mínimo | Token do Vault com permissões excessivas | Política Vault para AppRole do serviço restrita aos paths `secret/tmpl-rest-api/*` (princípio do menor privilégio) | NIST AC-3 |

#### D — Denial of Service

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Vault indisponível no startup | Serviço sobe sem secrets resolvidos | `spring.cloud.vault.fail-fast=true` garante que o serviço não inicia se Vault inacessível | INI-12 |

#### E — Elevation of Privilege

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Token Vault com excesso de permissões | AppRole com acesso a todos os paths do Vault | Política Vault restrita por path. Revisão de políticas documentada no control-mapping | NIST AC-3, INI-14 |

---

### 5.4 STRIDE — TB-04: tmpl-rest-api → Serviços Downstream

#### T — Tampering

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Manipulação de payload downstream | Payload modificado em trânsito entre serviços | TLS entre serviços (responsabilidade do service mesh / ingress). W3C TraceContext detecta divergências na rastreabilidade | INI-19 |

#### R — Repudiation

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Chamada downstream sem rastreabilidade | Serviço não registra chamadas saintes | Correlation-ID e TraceContext propagados. Audit trail para chamadas de modificação | INI-16, INI-17, INI-19 |

#### I — Information Disclosure

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Dados sensíveis em headers propagados | JWT ou secrets propagados sem necessidade para downstream | Apenas headers de correlação e trace são propagados. JWT não é repassado diretamente; re-autenticação via token de serviço se necessário | INI-16 |

---

### 5.5 STRIDE — TB-05: Kubernetes / Infra → Pod

#### S — Spoofing

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Imagem de container substituída | Imagem maliciosa implantada no registry | SBOM CycloneDX associado à imagem (digest). CI valida CVEs antes do push. Build reproduzível permite verificação de integridade | INI-22, INI-24, INI-26 |

#### I — Information Disclosure

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Secrets em env vars expostos via kubectl | `kubectl describe pod` revela env vars com secrets | Nenhum secret em env vars. Vault AppRole injeta secrets no processo via Spring Cloud Vault, não via env | RN-01 |
| Actuator expondo dados internos | `/actuator` endpoints expondo informações de ambiente | Apenas `health`, `health/liveness`, `health/readiness` e `prometheus` expostos. Endpoints como `/actuator/env` e `/actuator/beans` desabilitados | INI-08 |

#### D — Denial of Service

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Pod sem resource limits | Container consome CPU/memória ilimitada e degrada o node | Resource requests/limits definidos no Helm chart de referência (fora do escopo do app em si, documentado no deploy/) | INI-20 |
| Readiness probe falha incorretamente | Pod removido do LB por falso negativo | `/actuator/health/readiness` implementado via Spring Actuator com lógica de readiness do Spring WebFlux | INI-20 |

#### E — Elevation of Privilege

| Ameaça | Cenário | Mitigação implementada | CONTROL |
|--------|---------|------------------------|---------|
| Processo rodando como root no container | Vulnerabilidade de container escape com root dá acesso ao host | Dockerfile/Jib configurado com usuário não-root (UID 1000). `securityContext.runAsNonRoot: true` no pod spec de referência | RN-09 |
| Supply chain attack via dependência Maven | Dependência maliciosa injetada no build | CI executa Trivy para CVE scan. SBOM CycloneDX lista todas as dependências. CI falha em CVE crítica ou alta | INI-22, INI-25, RN-08 |

---

## 6. Ameaças Fora do Escopo (Out of Scope)

As ameaças listadas nesta seção foram explicitamente identificadas e documentadas como fora do escopo deste threat model. Elas devem ser tratadas pelo operador, pela infraestrutura, ou são escopo de phases futuras.

| ID | Ameaça | Razão para exclusão | Responsável |
|----|--------|---------------------|-------------|
| OOS-01 | Ataques de rede na camada TCP/IP (SYN flood, packet injection, ARP spoofing) | O serviço não gerencia TLS nem pilha de rede. Responsabilidade do ingress controller, WAF e team de infra de rede | Operador / Time de infra |
| OOS-02 | Comprometimento do host Kubernetes (kernel exploit, container breakout via vulnerabilidade do runtime) | Fora do controle do serviço. Responsabilidade do time de infra/SRE. Pod spec de referência inclui `securityContext` mínimo | Time de infra / SRE |
| OOS-03 | Ataques à supply chain do Maven Central registry (JAR malicioso substituído no repositório público) | Tratado parcialmente por Trivy + SBOM, mas um ataque de supply chain sofisticado (ex: typosquatting de novo artefato) está além do escopo do serviço. CISA Supply Chain Guidance aplica-se ao operador | Operador / CISA Guidance |
| OOS-04 | Comprometimento físico do datacenter ou servidor de Vault/Keycloak | Fora do escopo do template. Responsabilidade do time de infra e plano de DR/BCP do operador | Operador |
| OOS-05 | Ataque ao Keycloak em si (vulnerabilidades no IDP, comprometimento do admin do realm) | O template integra com Keycloak como componente externo confiável. Hardening do Keycloak é responsabilidade do operador | Operador / Time de segurança |
| OOS-06 | Ataques via engenharia social contra operadores (phishing de credenciais Vault/Keycloak) | Fora do escopo técnico do template. Tratado por políticas organizacionais e MFA obrigatório no Keycloak realm (INI-09) | Operador / RH / AppSec |
| OOS-07 | Ataques de timing side-channel criptográfico na validação JWT | A validação JWT é feita pela biblioteca Spring Security com algoritmos padrão (RS256). Ataques de timing em validação de assinatura RSA são considerados impraticáveis com bibliotecas modernas | Spring Security / JVM |
| OOS-08 | Vulnerabilidades zero-day na JVM (Java Virtual Machine) | Fora do controle do serviço. Gerenciado por processo de patch da imagem base e CVE scan do Trivy | Operador / Time de infra |
| OOS-09 | Denial of Service distribuído (DDoS) em escala de rede | Tratado por WAF e CDN na camada de ingress. O pod não tem capacidade de mitigar DDoS de forma isolada | Operador / Time de rede |
| OOS-10 | Gerenciamento do ciclo de vida de chaves privadas do Keycloak (rotação de RSA keypair) | O template valida tokens com chaves públicas (JWKS). A gestão das chaves privadas é responsabilidade do admin do Keycloak | Admin Keycloak |

---

## 7. Resumo de Controles Implementados

| CONTROL ID | Ameaça(s) STRIDE mitigada(s) | Componente | Referência federal |
|-----------|------------------------------|------------|-------------------|
| INI-04 | Elevation of Privilege, Spoofing | shared-identity, tmpl-rest-api | NIST AC-3, EO 14028 §4(b) |
| INI-05 | Elevation of Privilege | tmpl-rest-api (@PreAuthorize) | NIST AC-3, EO 14028 §4(b) |
| INI-06 | Spoofing (Keycloak falso) | tmpl-rest-api (env var) | NIST IA-9 |
| INI-07 | Spoofing, Tampering, Elevation | shared-identity (JwtDecoder) | NIST IA-9, SC-8 |
| INI-08 | Information Disclosure | tmpl-rest-api (actuator config) | NIST SI-10 |
| INI-09 | Spoofing (credenciais fracas) | Keycloak realm (MFA TOTP) | EO 14028 §3(b), NIST IA-5 |
| INI-10 | Information Disclosure | shared-secrets (Vault) | NIST SC-28, RN-01 |
| INI-11 | Information Disclosure | shared-secrets (mascaramento) | NIST SI-12 |
| INI-12 | Spoofing (Vault falso), DoS | shared-secrets (fail-fast) | NIST SC-28 |
| INI-14 | Elevation of Privilege | Vault policy (least privilege) | NIST AC-3 |
| INI-16 | Repudiation | shared-observability (logging) | NIST AU-2, AU-9 |
| INI-17 | Repudiation | shared-observability (audit trail) | NIST AU-2, AU-9, EO 14028 §3(c) |
| INI-19 | Repudiation, Tampering | shared-observability (OTel) | NIST AU-9, SI-12 |
| INI-20 | Denial of Service | tmpl-rest-api (probes) | NIST CP-10 |
| INI-21 | Denial of Service | shared-observability (graceful) | NIST CP-10 |
| INI-22 | Spoofing (imagem) | build/Dockerfile (SBOM) | EO 14028 §4(e), CISA Supply Chain |
| INI-24 | Spoofing (imagem) | build/Dockerfile (reprodutível) | EO 14028 §4(e) |
| INI-25 | Tampering (supply chain) | CI (Trivy CVE scan) | CISA Secure Supply Chain |
| RN-01 | Information Disclosure | shared-secrets, application.yml | NIST SC-28, EO 14028 §3(a) |
| RN-08 | Tampering (supply chain) | CI gate (CVE bloqueante) | CISA Secure Supply Chain |
| RN-09 | Elevation of Privilege | Dockerfile (non-root user) | CISA Secure by Design |
| RN-10 | Information Disclosure | GlobalExceptionHandler | NIST SI-10, SI-12 |

---

## 8. Referências

| Documento | Relevância |
|-----------|-----------|
| EO 14028 — Improving the Nation's Cybersecurity (2021) | Zero Trust Architecture (§4), Software Supply Chain (§4(e)), Audit Logging (§3(c)) |
| NIST SP 800-53 rev5 | AC-3 (Access Enforcement), IA-9 (Application Identity), SI-10 (Input Validation), AU-9 (Audit Protection), SC-8 (Transmission Confidentiality), SC-28 (Protection at Rest), CP-10 (Recovery) |
| CISA Secure by Design Guidance | Princípios de segurança por padrão, menor privilégio, eliminação de categorias de vulnerabilidade |
| CISA Software Supply Chain Security Guidance | Verificação de integridade de artefatos, SBOM, proveniência de software |
| OWASP Top 10 2021 | A01 Broken Access Control, A02 Cryptographic Failures, A03 Injection, A05 Security Misconfiguration, A09 Security Logging Failures |
| STRIDE Threat Modeling | Microsoft SDL Threat Modeling — metodologia aplicada neste documento |
| `tmpl-rest-api/control-mapping.md` | Mapeamento bidirecional de cada controle para cláusula federal específica |
| `shared-identity/src/` | Implementação do resource server JWT / Keycloak OIDC |
| `shared-secrets/src/` | Implementação da integração Vault / mascaramento de secrets |
| `shared-controls/src/` | SecurityHeadersFilter, GlobalExceptionHandler |
| `shared-observability/src/` | AuditTrailService, structured JSON logging, OTel tracing |
