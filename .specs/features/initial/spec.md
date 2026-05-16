# SPEC: Scaffold Inicial e Template REST API (Phase 1)

**Feature:** initial
**PRD:** [PRD_SecureMicro-Energy-Templates.md](../../docs/PRD_SecureMicro-Energy-Templates.md)
**Data:** 2026-05-16
**Status:** Draft
**Escopo:** Large (estrutura de repositório completa + template tmpl-rest-api end-to-end + documentação inicial + protótipo sme-cli)

---

## Problem Statement

Equipes de engenharia do setor de energia dos EUA que precisam criar novos microserviços ou modernizar módulos legados não dispõem de um ponto de partida production-grade alinhado às diretrizes federais (EO 14028, NIST SP 800-53, CISA). Os geradores de templates existentes (Spring Initializr, etc.) entregam serviços funcionais, mas sem autenticação federada com MFA, gerenciamento de segredos via vault, SBOM, rastreabilidade de controles federais ou threat model documentado. O resultado é um gap recorrente entre o que a regulação exige e o que as equipes conseguem implementar sem um time de platform engineering ou AppSec dedicado.

---

## Goals

- [ ] Criar a estrutura de repositório padrão da biblioteca SecureMicro-Energy-Templates
- [ ] Implementar o template `tmpl-rest-api` end-to-end com todos os controles de segurança obrigatórios
- [ ] Publicar documentação inicial: mapeamento de controles federais, threat model e quickstart para o tmpl-rest-api
- [ ] Entregar protótipo funcional do `sme-cli` com suporte ao comando `sme new tmpl-rest-api <project-name>`
- [ ] Garantir que o fluxo clone → rodando localmente seja completado em menos de 30 minutos

---

## Out of Scope

| Item | Razão |
|------|-------|
| Templates `tmpl-event-driven` e `tmpl-batch-job` | Escopo da Phase 2 conforme PRD |
| Revisão externa de AppSec | Escopo da Phase 2 — acontece antes do v1 release |
| Deployment em Kubernetes (Helm) completo e validado em produção | Reference deploy é suficiente para Phase 1 |
| Templates de infraestrutura (Terraform, VPC, IAM cloud) | Explicitamente fora do escopo do v1 inteiro |
| Templates de front-end | Explicitamente fora do escopo do v1 inteiro |
| Suporte a linguagens além de Java / Spring Boot | Roadmap Phase 3 |
| Certificação formal (FedRAMP, etc.) | Responsabilidade do operador |
| IDP customizado ou secrets manager próprio | Templates integram Keycloak e Vault; não os constroem |
| Sistemas instrumentados de segurança (safety-instrumented systems) | Fora do escopo do PRD |

---

## Atores

| Ator | Papel |
|------|-------|
| Engenheiro de energia (desenvolvedor) | Clona o template, configura e faz deploy de um microserviço |
| Revisor AppSec | Audita o baseline de controles implementado pelo template |
| Equipe de migração cloud | Usa o template como ponto de partida para modernização de módulo legado |
| Consultor / contratado independente | Entrega serviço hardened a cliente do setor de energia |
| sme-cli | Agente automatizado que instancia templates e inicializa configurações |

---

## User Stories

### INI-US-01: Estrutura de repositório padrão

**User Story:** Como engenheiro de energia, quero encontrar todos os módulos da biblioteca em uma estrutura de diretórios padronizada e documentada, para que eu saiba exatamente onde estão os templates, módulos compartilhados, documentação e referências de deployment.

**Acceptance Criteria:**

1. WHEN o repositório é clonado THEN SHALL existir a estrutura de diretórios:
   ```
   secure-micro-energy-templates/
   ├── tmpl-rest-api/
   ├── tmpl-event-driven/        (diretório reservado, com README indicando Phase 2)
   ├── tmpl-batch-job/           (diretório reservado, com README indicando Phase 2)
   ├── shared-controls/
   ├── shared-observability/
   ├── shared-identity/
   ├── shared-secrets/
   ├── sme-cli/
   ├── docs/
   │   ├── control-mappings/
   │   ├── threat-models/
   │   └── quickstart/
   └── reference-deploy/
   ```
2. WHEN o estado atual de cada componente difere do objetivo do PRD THEN cada diretório ou README SHALL declarar explicitamente seu status (ex: "Phase 1 — implementado", "Phase 2 — planejado")
3. WHEN qualquer template estiver presente THEN SHALL conter os subdiretórios padrão: `/src`, `/config`, `/security`, `/observability`, `/build`, `/deploy`, `/threat-model.md`, `/control-mapping.md`

---

### INI-US-02: Template tmpl-rest-api — Autenticação e Autorização

**User Story:** Como engenheiro de energia, quero que o template REST API já venha com autenticação OIDC via Keycloak e autorização por papéis configurada, para que eu não precise implementar esse controle do zero nem correr o risco de fazê-lo incorretamente.

**Acceptance Criteria:**

1. WHEN uma requisição chega sem token JWT válido THEN o serviço SHALL retornar HTTP 401
2. WHEN uma requisição chega com token JWT válido mas sem o papel (role) requerido THEN o serviço SHALL retornar HTTP 403
3. WHEN o serviço é iniciado THEN a configuração de discovery do Keycloak SHALL ser resolvida via variável de ambiente `KEYCLOAK_ISSUER_URI` — nunca hardcoded
4. WHEN o serviço valida o token THEN SHALL verificar assinatura, expiração e audience
5. WHEN o endpoint de health check é chamado (`/actuator/health`) THEN SHALL ser acessível sem autenticação
6. WHEN o template é instanciado THEN SHALL incluir configuração de MFA habilitado no realm de referência do Keycloak
7. WHEN credenciais do cliente Keycloak são necessárias THEN SHALL ser fornecidas via Vault — não via env vars ou arquivos de configuração em texto claro

---

### INI-US-03: Template tmpl-rest-api — Gerenciamento de Segredos

**User Story:** Como engenheiro de energia, quero que nenhum segredo (senhas, chaves, certificados, client secrets) apareça em variáveis de ambiente, arquivos de configuração ou no versionamento, para que o serviço atenda ao requisito federal de zero-secrets-in-config desde o primeiro commit.

**Acceptance Criteria:**

1. WHEN o repositório é escaneado THEN não SHALL existir nenhum valor de segredo em texto claro em nenhum arquivo versionado
2. WHEN o serviço é iniciado THEN todos os segredos SHALL ser resolvidos em tempo de execução via HashiCorp Vault através da biblioteca `shared-secrets`
3. WHEN um segredo expira ou é rotacionado no Vault THEN o serviço SHALL ser capaz de renová-lo sem reinicialização (lease renewal)
4. WHEN o template é entregue THEN SHALL conter documentação explícita de quais paths do Vault cada segredo deve ocupar
5. WHEN o CI executa THEN SHALL rodar varredura de segredos (ex: git-secrets ou trufflehog) e falhar o build se encontrar padrões de segredo

---

### INI-US-04: Template tmpl-rest-api — Observabilidade

**User Story:** Como engenheiro de energia ou revisor AppSec, quero que o serviço já gere logs estruturados em JSON com audit trail, métricas Micrometer e traces OpenTelemetry desde o primeiro commit, para que eu tenha visibilidade operacional e de segurança sem configuração adicional.

**Acceptance Criteria:**

1. WHEN qualquer requisição é processada THEN o serviço SHALL emitir log estruturado em JSON contendo: timestamp, correlation-id, método HTTP, path, status de resposta, duração, subject do token (se autenticado)
2. WHEN uma ação de modificação de dado ocorre THEN o serviço SHALL emitir evento de audit trail com: quem (subject), o quê (ação), quando (timestamp ISO 8601), resultado (sucesso/falha)
3. WHEN o serviço está em execução THEN SHALL expor métricas Micrometer em `/actuator/prometheus` incluindo: taxa de requisições, latência (p50/p95/p99), erros por tipo, uso de JVM
4. WHEN o serviço processa uma requisição THEN SHALL propagar trace context OpenTelemetry (W3C TraceContext) para serviços downstream
5. WHEN o serviço é iniciado THEN SHALL expor probes: `/actuator/health/liveness` e `/actuator/health/readiness`
6. WHEN nenhum coletor OpenTelemetry estiver disponível THEN o serviço SHALL iniciar normalmente (graceful degradation — sem crash no boot)

---

### INI-US-05: Template tmpl-rest-api — Supply Chain e SBOM

**User Story:** Como engenheiro de energia ou revisor AppSec, quero que cada imagem de container gerada pelo template inclua um SBOM (Software Bill of Materials) e seja construída de forma reproduzível, para atender ao requisito de proveniência de software do EO 14028.

**Acceptance Criteria:**

1. WHEN o build do container é executado THEN SHALL gerar SBOM no formato CycloneDX ou SPDX como artifact do build
2. WHEN o SBOM é gerado THEN SHALL incluir todas as dependências diretas e transitivas com versões pinadas
3. WHEN o build é executado duas vezes com o mesmo código-fonte e dependências THEN SHALL produzir imagem com mesmo digest (build reproduzível)
4. WHEN uma dependência com CVE crítica ou alta for detectada THEN o CI SHALL falhar o build e reportar a vulnerabilidade
5. WHEN a imagem de container é publicada THEN o SBOM SHALL ser associado à imagem (ex: OCI artifact ou atestado SLSA)

---

### INI-US-06: Template tmpl-rest-api — Documentação de Controles e Threat Model

**User Story:** Como revisor AppSec ou engenheiro de energia, quero que cada template entregue um mapeamento explícito de cada controle implementado para sua fonte federal correspondente (EO 14028, NIST 800-53, CISA) e um threat model STRIDE, para que eu possa auditar o baseline e verificar se as premissas do template se aplicam ao meu ambiente.

**Acceptance Criteria:**

1. WHEN o template é entregue THEN SHALL existir `/control-mapping.md` listando cada controle implementado mapeado à cláusula específica de EO 14028, NIST SP 800-53 ou CISA Guidance
2. WHEN o CI executa no template THEN SHALL verificar que `threat-model.md` existe e não está vazio — falhando o build se ausente
3. WHEN o threat model é entregue THEN SHALL conter: trust boundaries, atores, fluxo de dados, ameaças STRIDE analisadas e ameaças explicitamente fora do escopo
4. WHEN um controle é listado no control-mapping THEN SHALL haver código ou configuração correspondente no template que implemente aquele controle (rastreabilidade bidirecional)

---

### INI-US-07: Quickstart — Clone para rodando em menos de 30 minutos

**User Story:** Como engenheiro de energia sem time de platform engineering, quero seguir um guia de quickstart e ter o template tmpl-rest-api rodando localmente em menos de 30 minutos, incluindo Keycloak e Vault, para validar que o template funciona antes de adaptá-lo ao meu serviço.

**Acceptance Criteria:**

1. WHEN o quickstart é seguido do zero THEN a sequência de comandos SHALL resultar em: Keycloak rodando, Vault rodando, serviço tmpl-rest-api respondendo a requisições autenticadas — em menos de 30 minutos em máquina com Docker instalado
2. WHEN o Docker Compose do reference-deploy é iniciado THEN SHALL subir Keycloak, Vault e o serviço do template sem intervenção manual além de um único comando
3. WHEN o Vault é iniciado pelo reference-deploy THEN SHALL usar o modo dev para ambiente local — com documentação explícita de que mode dev NÃO é para produção
4. WHEN o quickstart é concluído THEN o usuário SHALL conseguir: obter token JWT do Keycloak, chamar endpoint autenticado do serviço, ver log estruturado, ver métricas em `/actuator/prometheus`
5. WHEN o guia de quickstart menciona configurações de produção THEN SHALL referenciar explicitamente as seções do PRD e da documentação de controles onde essas configurações são descritas

---

### INI-US-08: Protótipo sme-cli

**User Story:** Como engenheiro de energia, quero executar `sme new tmpl-rest-api meu-servico` e receber um projeto pronto para build com pacotes Java renomeados, para não precisar fazer renomeação manual de artefatos do template.

**Acceptance Criteria:**

1. WHEN `sme new tmpl-rest-api <project-name>` é executado THEN SHALL criar um novo diretório `<project-name>` com o conteúdo do tmpl-rest-api
2. WHEN o projeto é criado THEN SHALL renomear os pacotes Java de `com.securemicro.tmpl.restapi` para `com.securemicro.<project-name>` (ou nome configurável via flag)
3. WHEN o projeto é criado THEN SHALL atualizar `artifactId` e `name` no `pom.xml` para `<project-name>`
4. WHEN `sme new` é executado com template inexistente THEN SHALL exibir mensagem de erro clara listando os templates disponíveis
5. WHEN o CLI é executado THEN SHALL exibir versão via `sme --version`
6. [LACUNA: Não está definido se o sme-cli deve registrar automaticamente o client no Keycloak e os paths no Vault durante o `sme new`, ou se esse passo é manual/documentado. O PRD menciona essa intenção para o CLI mas não especifica o fluxo de credenciais necessárias para a integração automática em Phase 1.]

---

## Módulos Compartilhados

### INI-MS-01: shared-controls

- SHALL conter implementações reutilizáveis de controles de segurança comuns a todos os templates: validação de input, tratamento seguro de erros (sem vazamento de stack trace), headers de segurança HTTP (HSTS, CSP, X-Frame-Options)
- SHALL ser publicado como biblioteca JAR referenciada pelos templates via dependência Maven local

### INI-MS-02: shared-observability

- SHALL conter configuração padrão de logging estruturado JSON, Micrometer e OpenTelemetry
- SHALL exportar beans Spring configuráveis que os templates incluem por auto-configuração

### INI-MS-03: shared-identity

- SHALL conter a integração com Keycloak: configuração de resource server, extração de claims, utilitários de autorização baseada em roles
- [LACUNA: Não está definido se shared-identity deve também suportar Keycloak Admin API para registro automático de clients (necessário para sme-cli automatizado) ou apenas a validação de tokens no lado do serviço.]

### INI-MS-04: shared-secrets

- SHALL conter a integração com HashiCorp Vault: bootstrap de secrets no startup, renovação de lease, abstração de acesso a secret engines (KV v2 como referência)
- SHALL garantir que nenhum valor de segredo seja logado (mascaramento automático)

---

## Regras de Negócio e Controles de Segurança Obrigatórios

| ID | Controle | Aplicação |
|----|----------|-----------|
| RN-01 | Zero secrets em env vars, configs ou versionamento | Todos os templates e módulos compartilhados |
| RN-02 | Autenticação obrigatória via Keycloak OIDC com MFA habilitado no realm de referência | tmpl-rest-api e todos os templates futuros |
| RN-03 | SBOM gerado em cada build de container (CycloneDX ou SPDX) | Todos os templates |
| RN-04 | Logs estruturados JSON com audit trail em todas as ações de modificação | Todos os templates |
| RN-05 | Health/readiness/liveness probes presentes e funcionais | Todos os templates |
| RN-06 | Threat model STRIDE obrigatório por template — CI falha se ausente | Todos os templates |
| RN-07 | Control mapping com rastreabilidade para EO 14028 / NIST 800-53 / CISA por template | Todos os templates |
| RN-08 | Dependências com CVE crítica ou alta bloqueiam o build | Todos os templates |
| RN-09 | Build de container reproduzível (mesmo digest para mesmo código + dependências) | Todos os templates |
| RN-10 | Stack traces nunca expostos em respostas de API | tmpl-rest-api e todos os templates futuros |

---

## Edge Cases

- WHEN Vault está indisponível no startup THEN o serviço SHALL falhar fast com mensagem clara — nunca iniciar sem secrets resolvidos
- WHEN Keycloak está indisponível THEN o serviço SHALL rejeitar todas as requisições autenticadas com HTTP 503 enquanto o IDP estiver inacessível, sem expor detalhes do erro ao cliente
- WHEN um token JWT com `exp` expirado é apresentado THEN o serviço SHALL retornar HTTP 401 com body genérico (sem informação sobre o motivo exato da rejeição para o cliente externo, mas com log interno detalhado)
- WHEN o SBOM generation falha durante o build THEN o build SHALL falhar — SBOM não é opcional
- WHEN `sme new` é executado em diretório que já existe THEN o CLI SHALL abortar com erro sem sobrescrever conteúdo existente
- WHEN o quickstart é executado em máquina sem Docker THEN o guia SHALL detectar a ausência e exibir pré-requisito não atendido antes de prosseguir

---

## Requirement Traceability

| Req ID | User Story | Descrição resumida | Status |
|--------|------------|--------------------|--------|
| INI-01 | INI-US-01 | Estrutura de repositório criada com todos os diretórios padrão | Pending |
| INI-02 | INI-US-01 | Status de cada componente declarado explicitamente em README | Pending |
| INI-03 | INI-US-01 | Subdiretórios padrão presentes em tmpl-rest-api | Pending |
| INI-04 | INI-US-02 | HTTP 401 para requisições sem token JWT válido | Pending |
| INI-05 | INI-US-02 | HTTP 403 para token válido sem role requerido | Pending |
| INI-06 | INI-US-02 | Keycloak issuer via env var — não hardcoded | Pending |
| INI-07 | INI-US-02 | Validação de assinatura, expiração e audience do JWT | Pending |
| INI-08 | INI-US-02 | Health check acessível sem autenticação | Pending |
| INI-09 | INI-US-02 | MFA habilitado no realm de referência do Keycloak | Pending |
| INI-10 | INI-US-02 | Credenciais do client Keycloak fornecidas via Vault | Pending |
| INI-11 | INI-US-03 | Zero segredos em texto claro no repositório | Pending |
| INI-12 | INI-US-03 | Todos os segredos resolvidos via Vault em runtime | Pending |
| INI-13 | INI-US-03 | Lease renewal sem reinicialização do serviço | Pending |
| INI-14 | INI-US-03 | Documentação dos paths de Vault por segredo | Pending |
| INI-15 | INI-US-03 | CI com varredura de segredos — falha se encontrar padrão | Pending |
| INI-16 | INI-US-04 | Log JSON estruturado com campos obrigatórios por requisição | Pending |
| INI-17 | INI-US-04 | Audit trail para ações de modificação de dados | Pending |
| INI-18 | INI-US-04 | Métricas Micrometer em `/actuator/prometheus` | Pending |
| INI-19 | INI-US-04 | Propagação de trace context OpenTelemetry | Pending |
| INI-20 | INI-US-04 | Probes liveness e readiness | Pending |
| INI-21 | INI-US-04 | Graceful degradation quando coletor OTel indisponível | Pending |
| INI-22 | INI-US-05 | SBOM gerado em formato CycloneDX ou SPDX no build | Pending |
| INI-23 | INI-US-05 | SBOM inclui todas as dependências com versões pinadas | Pending |
| INI-24 | INI-US-05 | Build reproduzível (mesmo digest) | Pending |
| INI-25 | INI-US-05 | CI falha em CVE crítica ou alta | Pending |
| INI-26 | INI-US-05 | SBOM associado à imagem publicada | Pending |
| INI-27 | INI-US-06 | `/control-mapping.md` com rastreabilidade federal por controle | Pending |
| INI-28 | INI-US-06 | CI verifica existência e conteúdo de `threat-model.md` | Pending |
| INI-29 | INI-US-06 | Threat model com trust boundaries, atores, STRIDE, out-of-scope | Pending |
| INI-30 | INI-US-06 | Rastreabilidade bidirecional controle ↔ código | Pending |
| INI-31 | INI-US-07 | Clone → serviço rodando em menos de 30 minutos via quickstart | Pending |
| INI-32 | INI-US-07 | Docker Compose sobe Keycloak + Vault + serviço com um comando | Pending |
| INI-33 | INI-US-07 | Vault em modo dev para ambiente local com aviso explícito | Pending |
| INI-34 | INI-US-07 | Quickstart demonstra: token JWT, endpoint autenticado, logs, métricas | Pending |
| INI-35 | INI-US-08 | `sme new tmpl-rest-api <name>` cria projeto no diretório `<name>` | Pending |
| INI-36 | INI-US-08 | Renomeação de pacotes Java e artifactId no pom.xml | Pending |
| INI-37 | INI-US-08 | Erro claro para template inexistente com lista de disponíveis | Pending |
| INI-38 | INI-US-08 | `sme --version` funcional | Pending |

**Total:** 38 requisitos | 0 mapeados a tasks | 38 pendentes

---

## Success Criteria

- [ ] Repositório scaffoldado com a estrutura completa definida no PRD
- [ ] `tmpl-rest-api` compila, passa todos os testes e responde a requisições autenticadas
- [ ] Keycloak e Vault integrados — nenhum segredo em variáveis de ambiente ou arquivos versionados
- [ ] SBOM gerado automaticamente no build do container
- [ ] CI verifica: ausência de segredos, presença de threat-model, CVEs, SBOM
- [ ] `/control-mapping.md` e `threat-model.md` do tmpl-rest-api publicados e completos
- [ ] Quickstart validado: clone → rodando localmente em menos de 30 minutos
- [ ] `sme new tmpl-rest-api <name>` gera projeto compilável com pacotes renomeados
- [ ] 100% dos controles implementados mapeados a fonte federal identificada

---

## Lacunas Identificadas

| ID | Seção | Descrição |
|----|-------|-----------|
| LACUNA-01 | INI-US-08 / sme-cli | Não está definido se o `sme new` deve registrar automaticamente o client no Keycloak e os paths no Vault, ou se esse passo é manual. O PRD menciona a intenção mas não especifica o fluxo de credenciais administrativas necessárias para automação em Phase 1. |
| LACUNA-02 | INI-MS-03 / shared-identity | Não está definido se `shared-identity` deve expor integração com Keycloak Admin API (para registro automático de clients) além da validação de tokens. Isso impacta o escopo da biblioteca em Phase 1. |
| LACUNA-03 | Geral | Não está definido o mecanismo de renovação de lease do Vault para segredos de longa duração (ex: certificados mTLS futuros). A decisão de usar Spring Cloud Vault vs. Vault Agent Sidecar vs. integração manual deve ser tomada antes da implementação do shared-secrets. |
| LACUNA-04 | INI-US-05 / SBOM | Não está definido o formato preferencial entre CycloneDX e SPDX. Ambos atendem ao EO 14028, mas a escolha impacta tooling (Syft, Trivy, CycloneDX Maven Plugin). |
| LACUNA-05 | INI-US-07 / Quickstart | Não está definido se o reference-deploy Docker Compose deve incluir um coletor OpenTelemetry (ex: Jaeger, Grafana LGTM stack) para o quickstart local ou se o quickstart valida apenas logs e métricas. |
