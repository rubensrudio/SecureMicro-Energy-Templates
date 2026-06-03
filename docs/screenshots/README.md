# Screenshots — capture guide

> **Status:** placeholder. Screenshots require the `reference-deploy` Docker
> stack running. Docker was unavailable in the TASK-029 validation environment
> (see [`../validation/TASK-029-e2e-validation-report.md`](../validation/TASK-029-e2e-validation-report.md),
> AC-5/7/8 = NOT_TESTED). Capture these once the stack runs, drop the PNGs in
> this folder with the filenames below, and they will render in the README.

## How to bring the stack up

```bash
cd reference-deploy
docker compose up -d
# wait for healthchecks
docker compose ps
```

## Shots to capture

| Filename | Source | What it proves | Suggested annotation |
|---|---|---|---|
| `01-health.png` | `http://localhost:8080/actuator/health` | Service up, probes green | Highlight `"status":"UP"` |
| `02-auth-401.png` | `curl -i localhost:8080/api/v1/resources` (no token) | Unauthenticated rejected | Highlight `401` |
| `03-auth-200.png` | Same endpoint + valid bearer JWT | Authorized success path | Highlight `200` + role |
| `04-keycloak.png` | `http://localhost:8081` admin console | OIDC realm + client config | Show `securemicro` realm |
| `05-vault.png` | `http://localhost:8200` Vault UI (dev) | KV v2 secret paths (no values) | Mask any value |
| `06-prometheus.png` | `http://localhost:8080/actuator/prometheus` | Micrometer metrics exposed | Show `http_server_requests` |
| `07-jaeger-trace.png` | `http://localhost:16686` | End-to-end OTel trace span tree | Show a request trace |
| `08-audit-log.png` | service stdout | `event-type=AUDIT` JSON line on a write | Highlight `audit.action` |

## Conventions

- PNG, ~1440px wide, light theme for legibility.
- **Redact every real value**: tokens, passwords, internal hostnames. Dev-mode
  placeholders (`root`, `dev-placeholder-not-for-production`) are fine to show.
- Reference each shot from the README's **Screenshots** section.

> Until captured, the README links here so readers know the visual evidence is
> reproducible, not missing by oversight.
