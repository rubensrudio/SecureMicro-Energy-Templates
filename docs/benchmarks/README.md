# Benchmarks & metrics

> This is a **security-template** repository, not an ML project — there is no
> trained model, so "model metrics" do not apply. The metrics that matter here
> are **build/test health**, **supply-chain** (SBOM), **control coverage**, and
> **runtime performance**. Build/test/coverage numbers below are measured and
> reproducible today; runtime latency/throughput need the Docker stack and are
> documented as a methodology to fill in.

---

## 1. Build & test metrics (measured — TASK-029, 2026-05-17)

Source: [`../validation/TASK-029-e2e-validation-report.md`](../validation/TASK-029-e2e-validation-report.md).

| Metric | Value | How to reproduce |
|---|---|---|
| Java tests | **31 passed**, 0 failed, 0 errors, 0 skipped | `mvn clean verify` |
| sme-cli tests | **62 passed** in 0.42s | `cd sme-cli && pytest -q` |
| Reactor build time | **52.9s** (6 modules, cold) | `mvn clean install` |
| Slowest module | tmpl-rest-api — 24.7s | reactor summary |
| SBOM components | **166** (CycloneDX 1.4) | `tmpl-rest-api/target/bom.json` |
| Controls mapped | **21** unique (RN + INI) | `control-mapping.md` |
| Control ↔ code traceability | **100%** — every control has ≥1 `CONTROL:` ref | AC-11 |
| Secrets in repo (real) | **0** | AC-4 (grep / trufflehog) |

### Per-module build time

| Module | Time |
|---|---|
| shared-controls | 5.5s |
| shared-observability | 3.6s |
| shared-identity | 8.4s |
| shared-secrets | 10.2s |
| tmpl-rest-api | 24.7s |
| **Total reactor** | **52.9s** |

---

## 2. Supply-chain / CVE gate

| Metric | Target | Mechanism |
|---|---|---|
| SBOM per build | required | CycloneDX Maven plugin (`makeAggregateBom`) → `bom.xml` + `bom.json` |
| Critical/High CVEs | **0** (build fails otherwise) | CVE gate in CI (EO 14028 §4(e), NIST SI-2) |
| Secret scanning | clean | `trufflehog` on every PR |

---

## 3. Runtime performance — methodology (pending Docker run)

Not yet measured: the validation environment had no Docker (AC-5/7/8 =
NOT_TESTED). Run against the `reference-deploy` stack and record results here.

### Suggested harness

```bash
# warm stack first
cd reference-deploy && docker compose up -d

# authenticated load with k6 (token injected via env)
k6 run docs/benchmarks/load.js   # to be authored
```

### Numbers to capture

| Metric | What | Target (initial) |
|---|---|---|
| p50 / p95 / p99 latency | authorized `GET /api/v1/resources` | p95 < 50ms (no I/O) |
| Throughput | sustained req/s, 4 vCPU | record baseline |
| JVM cold start | process up → first 200 | record baseline |
| JWT validation overhead | with vs without auth filter | < 5ms (JWKS cached) |
| Memory | RSS at idle / under load | record baseline |
| Vault startup fetch | context-start delta with Vault | record baseline |

Capture `/actuator/prometheus` snapshots (`http_server_requests_seconds`,
`jvm_memory_used_bytes`) before/after load and attach a Jaeger trace
([`../screenshots/`](../screenshots/README.md), shot 07).

> Keep this honest: mark each row **measured** or **pending** with date +
> environment. Don't publish synthetic numbers as if observed.
