# reference-deploy/keycloak

## WARNING: DEV MODE ONLY — NOT FOR PRODUCTION

This directory contains the Keycloak realm export used exclusively for local
development and quickstart validation.

The `realm-export.json` file **MUST NOT** be imported into any shared, staging,
or production environment without a security review and secret rotation.

---

## Contents

| File | Purpose |
|------|---------|
| `realm-export.json` | Keycloak realm definition for `tmpl-rest-api` — imported automatically at container startup via `--import-realm` |

---

## Control Traceability

### CONTROL: INI-09 — MFA TOTP mandatory

MFA is made mandatory through the following settings in `realm-export.json`:

- `otpPolicyType: totp` — realm uses TOTP as OTP algorithm
- `requiredActions[CONFIGURE_TOTP].defaultAction: true` — all users must
  configure OTP on first login
- `browserFlow: browser-with-otp` — custom authentication flow that enforces
  OTP after username/password

### CONTROL: RN-01 — Zero secrets in repository

The `client.secret` field in `realm-export.json` holds the placeholder value
`${VAULT_MANAGED}`. This is an **operational instruction**, not a credential.

Before using this realm in any non-local environment:
1. Rotate the client secret via the Keycloak Admin Console.
2. Store the real secret in HashiCorp Vault at the KV v2 path:
   `secret/tmpl-rest-api/keycloak` (key: `client-secret`).
3. Reference the Vault path in `tmpl-rest-api/config/vault-paths.md`.

See `reference-deploy/vault/vault-dev-init.sh` for the dev seed values used
in the local Docker Compose environment.

### CONTROL: RN-02 — Keycloak OIDC realm for JWT validation with MFA enabled

The realm `tmpl-rest-api` is the OIDC identity provider for the `tmpl-rest-api`
service. It issues JWTs validated by the service via `KEYCLOAK_ISSUER_URI`
(never hardcoded — AC INI-06). MFA is enabled as described in CONTROL: INI-09
above.

---

## How the realm import works

The `docker-compose.yml` bind-mounts this entire directory as
`/opt/keycloak/data/import/` (read-only) inside the Keycloak container.
The Keycloak startup flag `--import-realm` instructs the `DirImportProvider`
to scan that directory for `*.json` files and import each realm it finds.

**Why directory-level bind-mount instead of file-level?**

When the target path (`/opt/keycloak/data/import/`) does not pre-exist in the
container image, a file-level bind-mount causes Docker to create the full path
as nested directories, silently turning `realm-export.json` into an empty
directory. `DirImportProvider` then reports "Import finished successfully"
without processing anything (silent failure). Mounting the host directory
avoids this race condition entirely.

---

## Production guidance

1. Export the realm from the production Keycloak Admin Console after
   configuring MFA flows, client secrets, and user federation.
2. Never commit realm exports containing real secrets to version control.
3. Use Keycloak's built-in export mechanism (`kc.sh export`) to generate a
   clean export, and scrub any `secret` fields before committing.
4. For automated provisioning, use the Keycloak Admin REST API or a Terraform
   provider — do not rely on `--import-realm` in production.
