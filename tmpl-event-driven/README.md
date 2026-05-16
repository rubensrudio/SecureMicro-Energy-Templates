# tmpl-event-driven

**Status: Phase 2 — planejado**

---

## Propósito

Este diretório reserva o template `tmpl-event-driven` para a biblioteca
SecureMicro-Energy-Templates.

O `tmpl-event-driven` fornecerá um ponto de partida production-grade para
microserviços orientados a eventos no setor de energia dos EUA. O template
codificará, desde o primeiro commit, os controles de segurança exigidos pelo
EO 14028, NIST SP 800-53 e CISA Guidance para arquiteturas baseadas em
mensageria.

## Stack planejada

| Componente          | Tecnologia planejada                                      |
|---------------------|-----------------------------------------------------------|
| Framework           | Spring Boot 3.x + Spring WebFlux (reativo)                |
| Mensageria          | Apache Kafka (produtor + consumidor)                      |
| Autenticação        | Keycloak OIDC com validação de JWT por mensagem           |
| Secrets management  | HashiCorp Vault via Spring Cloud Vault (KV v2)            |
| Observabilidade     | Logs JSON estruturados, Micrometer, OpenTelemetry (OTLP)  |
| SBOM                | CycloneDX via cyclonedx-maven-plugin                      |
| Build               | Maven Multi-Module, build reproduzível via Jib            |

## Controles previstos

- Zero secrets em variáveis de ambiente, arquivos de configuração ou no
  versionamento (RN-01 / EO 14028 Sec. 4)
- Autenticação obrigatória com validação de JWT por mensagem consumida (RN-02)
- Logs de audit trail para cada evento processado (RN-04)
- SBOM gerado em cada build de container (RN-03 / EO 14028 Sec. 4(e))
- Threat model STRIDE obrigatório — CI falha se ausente (RN-06)
- Mapeamento de controles para EO 14028 / NIST SP 800-53 / CISA (RN-07)

## Por que está fora do escopo da Phase 1

Conforme o PRD e o spec da feature `initial`, os templates `tmpl-event-driven`
e `tmpl-batch-job` foram explicitamente adiados para a Phase 2 para manter o
escopo da Phase 1 focado no `tmpl-rest-api` end-to-end e na infraestrutura
compartilhada (`shared-controls`, `shared-observability`, `shared-identity`,
`shared-secrets`).

A revisão externa de AppSec, prevista antes do v1 release, também ocorre na
Phase 2 e incluirá este template.

## Referencia

PRD completo: [docs/PRD_SecureMicro-Energy-Templates.md](../docs/PRD_SecureMicro-Energy-Templates.md)
