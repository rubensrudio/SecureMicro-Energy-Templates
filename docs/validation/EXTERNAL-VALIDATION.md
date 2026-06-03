# External validation

Current evidence is **internal**: the [TASK-029 E2E validation report](TASK-029-e2e-validation-report.md)
(9 PASS / 3 NOT_TESTED). To make the federal-alignment claims credible to
third parties, pursue the external attestations below. This file is the
tracker — flip each row to ✅ with a link as it lands.

---

## 1. Independent / automated attestations to obtain

| Validation | What it proves | How | Badge | Status |
|---|---|---|---|---|
| **OpenSSF Best Practices** | Open-source security hygiene baseline | Self-cert at [bestpractices.coreinfrastructure.org](https://www.bestpractices.dev/) | yes | ☐ pending |
| **OpenSSF Scorecard** | Automated repo security posture (branch protection, signed releases, CI, pinned deps) | GitHub Action `ossf/scorecard-action` | yes | ☐ pending |
| **SLSA provenance** | Tamper-evident build provenance for release artifacts | `slsa-framework/slsa-github-generator` | yes | ☐ pending |
| **Signed SBOM attestation** | SBOM bound to artifact, signed | `cosign attest` + the CycloneDX `bom.json` | — | ☐ pending |
| **Container/JAR signing** | Artifact authenticity | Sigstore `cosign sign` | — | ☐ pending |
| **CVE scan (public)** | No known critical/high CVEs | Trivy / Grype + Dependabot, results published | yes | ☐ pending |
| **CodeQL** | Static analysis (SAST) clean | GitHub CodeQL Action | yes | ☐ pending |

---

## 2. Human / domain external review

| Validation | Who | Status |
|---|---|---|
| Independent AppSec review of STRIDE threat model | external security engineer (not the author) | ☐ pending |
| NIST SP 800-53 control-mapping review | compliance/GRC reviewer | ☐ pending |
| Quickstart reproduced on a clean machine (≤30 min, AC-5) | external developer | ☐ pending |
| Full Docker stack run (AC-5/7/8 closure) | reviewer with Docker | ☐ pending |

---

## 3. Quick wins (do these first)

1. **Add CI badges** — Scorecard, CodeQL, CVE scan run on push and surface a
   live badge in the README. Lowest effort, highest credibility per hour.
2. **Sign the v0.1.0 release** — `cosign sign-blob` the JARs + attach the signed
   SBOM. Turns "we generate an SBOM" into "here is the signed SBOM for this tag."
3. **OpenSSF Best Practices self-cert** — questionnaire, no infra, yields a badge.

### Example: Scorecard workflow (`.github/workflows/scorecard.yml`)

```yaml
name: Scorecard
on:
  branch_protection_rule:
  schedule: [{ cron: "0 6 * * 1" }]
  push: { branches: [main] }
permissions: read-all
jobs:
  analysis:
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      id-token: write
    steps:
      - uses: actions/checkout@v4
        with: { persist-credentials: false }
      - uses: ossf/scorecard-action@v2
        with: { results_file: results.sarif, results_format: sarif, publish_results: true }
      - uses: github/codeql-action/upload-sarif@v3
        with: { sarif_file: results.sarif }
```

---

> **Honesty rule:** do not claim a validation in the README until its row here
> is ✅ with a verifiable link. Internal ≠ external — keep the distinction.
