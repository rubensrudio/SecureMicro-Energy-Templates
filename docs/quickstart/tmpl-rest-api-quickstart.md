# tmpl-rest-api Quickstart Guide

<!-- CONTROL: INI-31 -->

**Objetivo:** Levar um desenvolvedor do clone ao primeiro endpoint autenticado em menos de 30 minutos.

**Stack do ambiente local:** Docker Compose com 4 serviços — Keycloak (8180), Vault (8200),
tmpl-rest-api (8080), Jaeger (16686).

> **Tempo estimado:** 20–25 minutos (excluindo download de imagens, ver Passo 3).

---

## Sumário

1. [Pré-requisitos](#1-pré-requisitos)
2. [Clone e navegação](#2-clone-e-navegação)
3. [Download das imagens](#3-download-das-imagens)
4. [Subir os serviços](#4-subir-os-serviços)
5. [Seed de segredos no Vault](#5-seed-de-segredos-no-vault)
6. [Obter token JWT do Keycloak](#6-obter-token-jwt-do-keycloak)
7. [Chamar endpoint autenticado](#7-chamar-endpoint-autenticado)
8. [Observabilidade](#8-observabilidade)
9. [Checklist de Produção](#9-checklist-de-produção)

---

## 1. Pré-requisitos

Verifique cada item antes de começar:

| Ferramenta | Versão mínima | Verificação |
|------------|--------------|-------------|
| Docker Engine | 24.0+ | `docker version` |
| Docker Compose plugin | v2.20+ | `docker compose version` |
| git | qualquer | `git --version` |
| curl | qualquer | `curl --version` |
| Java 17 | 17+ | `java -version` (apenas para build local) |

### Verificar pré-requisitos

```bash
docker compose version
```

**Output esperado:**
```
Docker Compose version v2.20.0
```

**Se o comando falhar**, o Docker Compose plugin v2 não está instalado.
Instale seguindo: <https://docs.docker.com/compose/install/>

> **Nota:** Se você usa uma versão mais antiga do Docker Compose (v1, comando `docker-compose`),
> atualize para o plugin v2 antes de prosseguir. Os comandos abaixo usam a sintaxe `docker compose`
> (sem hífen).

---

## 2. Clone e navegação

```bash
git clone <repo-url> SecureMicro-Energy-Templates
cd SecureMicro-Energy-Templates
cd reference-deploy
```

Confirme que você está no diretório correto:

```bash
ls docker-compose.yml
```

**Output esperado:**
```
docker-compose.yml
```

---

## 3. Download das imagens

Faça o pull das imagens **antes** de subir os serviços. Isso separa erros de rede de erros
de configuração e não conta no cronômetro dos 30 minutos:

```bash
docker compose pull
```

**Output esperado (resumido):**
```
[+] Pulling 4/4
 ✔ keycloak Pulled
 ✔ vault Pulled
 ✔ jaeger Pulled
 ✔ tmpl-rest-api Pulled
```

> **Nota:** Em redes lentas este passo pode levar vários minutos. Execute-o antes de
> iniciar o cronômetro do quickstart.

---

## 4. Subir os serviços

<!-- CONTROL: INI-31 -->

> **WARNING: DEV MODE ONLY — NOT FOR PRODUCTION**
>
> Este Compose usa Vault em modo dev (root token), Keycloak com H2 em memória e HTTP puro.
> Nenhuma dessas configurações é aceitável em produção. Ver [Checklist de Produção](#9-checklist-de-produção).

```bash
docker compose up -d
```

**Output esperado:**
```
[+] Running 4/4
 ✔ Container securemicro-vault         Started
 ✔ Container securemicro-keycloak      Started
 ✔ Container securemicro-jaeger        Started
 ✔ Container securemicro-tmpl-rest-api Started
```

Aguarde todos os containers ficarem `healthy`. O serviço `tmpl-rest-api` aguarda Keycloak e
Vault estarem prontos antes de iniciar (dependência com `condition: service_healthy`):

```bash
docker compose ps
```

**Output esperado (todos healthy):**
```
NAME                          IMAGE                                    STATUS
securemicro-jaeger            jaegertracing/all-in-one:1.57            Up (healthy)
securemicro-keycloak          quay.io/keycloak/keycloak:24.0.4         Up (healthy)
securemicro-tmpl-rest-api     securemicro/tmpl-rest-api:latest         Up (healthy)
securemicro-vault             hashicorp/vault:1.17.5                   Up (healthy)
```

> **Dica:** Se algum container ficar em `starting` por mais de 3 minutos, verifique os logs:
> `docker compose logs <nome-do-servico>`

---

## 5. Seed de segredos no Vault

> **WARNING: DEV MODE ONLY — NOT FOR PRODUCTION**
>
> O script abaixo popula o Vault em modo dev com valores placeholder.
> Esses valores não concedem acesso a nenhum sistema real.

```bash
sh vault/vault-dev-init.sh
```

**Output esperado:**
```
================================================================
  WARNING: DEV MODE ONLY — NOT FOR PRODUCTION
  Vault seed script for local development
  VAULT_ADDR: http://127.0.0.1:8200
================================================================

Step 1/4 — Enabling KV v2 engine at secret/ ...
Step 2/4 — Seeding secret/tmpl-rest-api/keycloak ...
  [OK] secret/tmpl-rest-api/keycloak written.
Step 3/4 — Seeding secret/tmpl-rest-api/service ...
  [OK] secret/tmpl-rest-api/service written.
Step 4/4 — Seeding secret/tmpl-rest-api/db ...
  [OK] secret/tmpl-rest-api/db written.

================================================================
  Verification — confirming seeded paths are readable:
================================================================

OK: secret/tmpl-rest-api/keycloak seeded
OK: secret/tmpl-rest-api/service seeded
OK: secret/tmpl-rest-api/db seeded

================================================================
  Vault seed complete.

  WARNING: DEV MODE ONLY — all values are placeholders.
  Replace with real credentials before any non-local deployment.
================================================================
```

### Paths do Vault populados

| Path no Vault | Chave | Consumidor |
|---------------|-------|-----------|
| `secret/tmpl-rest-api/keycloak` | `client-secret` | shared-identity |
| `secret/tmpl-rest-api/service` | `api-key` | tmpl-rest-api controllers |
| `secret/tmpl-rest-api/db` | `password` | reservado para uso futuro |

---

## 6. Obter token JWT do Keycloak

> **WARNING: DEV MODE ONLY — NOT FOR PRODUCTION**
>
> O `client-secret` abaixo é um placeholder de desenvolvimento. Em produção, o client-secret
> deve ser gerenciado via Vault dynamic secrets — nunca exposto em scripts ou variáveis de ambiente.

### 6.1 Obter o client-secret

O `client-secret` do realm `tmpl-rest-api` pode ser obtido de duas formas:

**Opção A — Keycloak Admin Console (recomendado para quickstart):**

1. Abra <http://localhost:8180/admin> no browser.
2. Faça login com `admin` / `dev-placeholder-not-for-production`.
3. Selecione o realm **tmpl-rest-api** no dropdown superior esquerdo.
4. Navegue em **Clients** → **tmpl-rest-api** → aba **Credentials**.
5. Copie o valor de **Client secret**.

**Opção B — Via Vault (valor seed do dev):**

```bash
VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=root \
  vault kv get -field=client-secret secret/tmpl-rest-api/keycloak
```

**Output esperado:**
```
dev-placeholder-not-for-production
```

### 6.2 Solicitar token via client_credentials

Substitua `<CLIENT_SECRET>` pelo valor obtido no passo 6.1:

```bash
TOKEN=$(curl -s \
  -X POST http://localhost:8180/realms/tmpl-rest-api/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=tmpl-rest-api" \
  -d "client_secret=<CLIENT_SECRET>" \
  | jq -r '.access_token')

echo "Token obtido: ${TOKEN:0:50}..."
```

**Output esperado:**
```
Token obtido: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6...
```

> **Pré-requisito:** `jq` deve estar instalado. Alternativa sem `jq`:
> ```bash
> curl -s -X POST http://localhost:8180/realms/tmpl-rest-api/protocol/openid-connect/token \
>   -H "Content-Type: application/x-www-form-urlencoded" \
>   -d "grant_type=client_credentials&client_id=tmpl-rest-api&client_secret=<CLIENT_SECRET>"
> ```
> Copie o valor de `access_token` manualmente e exporte como `TOKEN=<valor>`.

---

## 7. Chamar endpoint autenticado

### 7.1 Endpoint de health (sem autenticação)

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

**Output esperado:**
```json
{
  "status": "UP"
}
```

### 7.2 Chamada autenticada — HTTP 200

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/health \
  | jq .
```

**Output esperado:**
```json
{
  "status": "UP"
}
```

### 7.3 Chamada não-autenticada — HTTP 401

```bash
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/v1/resources
```

**Output esperado:**
```
401
```

O serviço retorna HTTP 401 para qualquer requisição sem token JWT válido nos endpoints
protegidos (AC INI-04 / AC INI-07).

### 7.4 Endpoint de exemplo com role (HTTP 200 ou 403)

```bash
curl -s -w "\nHTTP: %{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/hello
```

**Output esperado (token com `ROLE_SERVICE_USER`):**
```
{"message":"Hello from tmpl-rest-api"}
HTTP: 200
```

**Output esperado (token sem a role requerida):**
```
{"error":"forbidden"}
HTTP: 403
```

---

## 8. Observabilidade

<!-- CONTROL: INI-34 -->

### 8.1 Logs estruturados JSON

```bash
docker compose logs tmpl-rest-api | tail -5
```

**Output esperado (cada linha é um objeto JSON):**
```json
{"timestamp":"2026-05-17T10:15:30.123Z","level":"INFO","correlation-id":"abc-123","http.method":"GET","http.path":"/api/v1/hello","http.status":200,"duration_ms":42,"auth.subject":"service-account-tmpl-rest-api","service.name":"tmpl-rest-api"}
```

Campos obrigatórios presentes: `timestamp`, `level`, `correlation-id`, `http.method`,
`http.path`, `http.status`, `duration_ms`, `auth.subject`, `service.name`.

### 8.2 Métricas Prometheus

```bash
curl -s http://localhost:8080/actuator/prometheus | head -5
```

**Output esperado:**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",...} 5.2428E7
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
```

Métricas disponíveis incluem: `http_server_requests_*`, `jvm_*`, `process_*`.

### 8.3 Jaeger UI — Distributed Tracing

<!-- CONTROL: INI-34 -->

Abra no browser: <http://localhost:16686>

1. No campo **Service**, selecione `tmpl-rest-api`.
2. Clique em **Find Traces**.
3. Os traces das requisições feitas no Passo 7 devem aparecer na lista.

Cada trace exibe: spans individuais, duração, propagação de contexto W3C TraceContext
(AC INI-19) e correlação com o `correlation-id` dos logs JSON.

---

## 9. Checklist de Produção

<!-- CONTROL: INI-35 -->

> **WARNING: DEV MODE ONLY — NOT FOR PRODUCTION**
>
> O ambiente montado por este quickstart usa configurações de desenvolvimento que
> **nunca devem ser usadas em produção**. O Vault em modo dev armazena dados em memória
> (perdidos ao reiniciar), usa o token `root` sem TTL, e não tem TLS.
>
> Antes de fazer deploy em qualquer ambiente não-local, o operador DEVE completar
> todos os itens abaixo. Para detalhes de cada controle, consulte:
> - `tmpl-rest-api/control-mapping.md` — rastreabilidade para EO 14028 / NIST SP 800-53 / CISA
> - `PRD_SecureMicro-Energy-Templates.md` — decisões arquiteturais e trade-offs

### Itens obrigatórios antes de ir para produção

| # | Componente | O que fazer |
|---|-----------|------------|
| 1 | **Vault** | Substituir modo dev por Vault em HA com backend persistente (Raft ou Consul). Habilitar TLS em todas as conexões. Trocar autenticação por `root` token para AppRole ou Kubernetes auth com TTL curto. Habilitar audit logging. |
| 2 | **Keycloak** | Migrar banco de dados de H2 em memória para PostgreSQL. Habilitar HTTPS em todas as comunicações (desabilitar `KC_HTTP_ENABLED`). Rotacionar a senha de admin (nunca usar `dev-placeholder-not-for-production`). Configurar réplicas de alta disponibilidade. |
| 3 | **tmpl-rest-api** | Construir e publicar a imagem em registry privado com tag imutável (nunca usar `:latest` em produção). Assinar a imagem (Sigstore/Cosign). Associar SBOM CycloneDX à imagem publicada (AC INI-26). |
| 4 | **Secrets** | Remover todos os valores `dev-placeholder-not-for-production` do Vault. Gerar `client-secret` do Keycloak de forma aleatória e armazená-lo como Vault dynamic secret. Nunca expor segredos em variáveis de ambiente em texto claro. |
| 5 | **Rede** | Habilitar TLS end-to-end em todas as comunicações entre serviços. Remover exposição de portas internas (Vault :8200, Keycloak interno) ao plano público. Implementar network policies para isolar comunicação entre containers/pods. |
| 6 | **MFA** | Verificar que o realm Keycloak importado tem MFA TOTP obrigatório para todos os usuários (AC INI-09). Testar o fluxo de autenticação com TOTP antes do go-live. |
| 7 | **SBOM e CVE** | Executar `mvn verify` com o `cyclonedx-maven-plugin` e garantir que não há CVEs críticas ou altas nas dependências (AC INI-25). Integrar scan de CVE no pipeline de CI antes de cada deploy. |

> **Referência de compliance:** Cada item acima está mapeado a controles específicos de
> EO 14028, NIST SP 800-53 r5 e CISA em `tmpl-rest-api/control-mapping.md`.
> Consulte esse documento para rastreabilidade completa antes de uma auditoria.

---

## Encerrar o ambiente local

```bash
docker compose down -v
```

O flag `-v` remove os volumes nomeados, garantindo que os dados do Keycloak e Vault em memória
sejam descartados. Próxima execução de `docker compose up -d` começa com estado limpo.

---

## Troubleshooting

### Container travado em `starting`

```bash
docker compose logs <nome-do-servico>
```

Causas comuns:
- `tmpl-rest-api`: Vault ou Keycloak ainda não estão `healthy`. Aguarde e tente `docker compose ps`.
- `keycloak`: porta 8180 em uso. Verifique com `netstat -an | grep 8180`.
- `vault`: porta 8200 em uso. Verifique com `netstat -an | grep 8200`.

### Token JWT inválido (401 inesperado)

- Verifique se o token expirou: tokens de `client_credentials` têm TTL curto (padrão: 5 minutos no Keycloak).
- Repita o Passo 6.2 para obter um novo token.

### Vault seed falha com "connection refused"

- O Vault pode ainda não estar pronto. Aguarde o container ficar `healthy`:
  ```bash
  docker compose ps vault
  ```
- Reexecute `sh vault/vault-dev-init.sh` após o status ser `healthy`.
