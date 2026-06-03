# tmpl-batch-job

**Status: Phase 2 — planejado**

---

## Propósito

Este diretório reserva o template `tmpl-batch-job` para a biblioteca
SecureMicro-Energy-Templates.

O `tmpl-batch-job` fornecerá um ponto de partida production-grade para jobs
de processamento em lote no setor de energia dos EUA. O template codificará,
desde o primeiro commit, os controles de segurança exigidos pelo EO 14028,
NIST SP 800-53 e CISA Guidance para cargas de trabalho batch, incluindo
rastreabilidade de auditoria para cada registro processado.

## Stack planejada

| Componente          | Tecnologia planejada                                      |
|---------------------|-----------------------------------------------------------|
| Framework           | Spring Boot 3.x + Spring Batch                            |
| Agendamento         | Spring Scheduler ou integração com Quartz (configurável)  |
| Autenticação        | Keycloak OIDC — identidade do job via service account JWT |
| Secrets management  | HashiCorp Vault via Spring Cloud Vault (KV v2)            |
| Observabilidade     | Logs JSON estruturados, Micrometer, OpenTelemetry (OTLP)  |
| SBOM                | CycloneDX via cyclonedx-maven-plugin                      |
| Build               | Maven Multi-Module, build reproduzível via Jib            |

## Controles previstos

- Zero secrets em variáveis de ambiente, arquivos de configuração ou no
  versionamento (RN-01 / EO 14028 Sec. 4)
- Identidade de serviço autenticada via JWT com service account (RN-02)
- Audit trail por item processado: quem, o que, quando, resultado (RN-04)
- SBOM gerado em cada build de container (RN-03 / EO 14028 Sec. 4(e))
- Threat model STRIDE obrigatório — CI falha se ausente (RN-06)
- Mapeamento de controles para EO 14028 / NIST SP 800-53 / CISA (RN-07)
- Restart idempotente: re-execucao do job nao duplica registros (controle
  especifico de batch)

## Por que está fora do escopo da Phase 1

Conforme o PRD e o spec da feature `initial`, os templates `tmpl-event-driven`
e `tmpl-batch-job` foram explicitamente adiados para a Phase 2 para manter o
escopo da Phase 1 focado no `tmpl-rest-api` end-to-end e na infraestrutura
compartilhada (`shared-controls`, `shared-observability`, `shared-identity`,
`shared-secrets`).

A revisao externa de AppSec, prevista antes do v1 release, tambem ocorre na
Phase 2 e incluira este template.

## Referencia

PRD completo: [docs/PRD_SecureMicro-Energy-Templates.md](../docs/PRD_SecureMicro-Energy-Templates.md)
