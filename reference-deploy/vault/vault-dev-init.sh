#!/usr/bin/env sh
# =============================================================================
# reference-deploy/vault/vault-dev-init.sh
#
# WARNING: DEV MODE ONLY — NOT FOR PRODUCTION
#
# Seeds HashiCorp Vault (dev mode) with the KV v2 paths required by
# tmpl-rest-api.  This script is intended exclusively for local quickstart.
#
# All values stored here are DEV PLACEHOLDERS.  They are:
#   - Meaningless credentials that grant no real access to any system
#   - Never used in any environment beyond a developer's local machine
#   - Never committed as real secrets (this script is safe to version-control)
#
# For production:
#   - Use real, randomly-generated credentials
#   - Provision paths via Vault API or Terraform with proper ACL policies
#   - Use AppRole or Kubernetes auth instead of the root token
#   - Never use Vault dev mode in production
#
# Usage (run against a local Vault dev server):
#   export VAULT_ADDR=http://127.0.0.1:8200
#   export VAULT_TOKEN=root
#   sh reference-deploy/vault/vault-dev-init.sh
#
# Script is idempotent: `vault kv put` overwrites existing values on each run.
#
# CONTROL: RN-01 — all values are dev-placeholder-not-for-production;
#   zero real secrets in this file.
# =============================================================================

set -eu

# ---------------------------------------------------------------------------
# Configuration
#
# VAULT_ADDR and VAULT_TOKEN are expected to be set in the calling environment.
# Default values below are for the Docker Compose dev setup only.
#
# WARNING: DEV MODE ONLY — root token must NEVER be used in production.
# ---------------------------------------------------------------------------
VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"

export VAULT_ADDR
export VAULT_TOKEN

echo "================================================================"
echo "  WARNING: DEV MODE ONLY — NOT FOR PRODUCTION"
echo "  Vault seed script for local development"
echo "  VAULT_ADDR: ${VAULT_ADDR}"
echo "================================================================"
echo ""

# ---------------------------------------------------------------------------
# Step 1 — Enable KV v2 engine at 'secret/'
#
# Vault dev mode enables a KV v1 engine at 'secret/' by default.
# We enable KV v2 explicitly so that the Spring Cloud Vault kv.backend
# configuration aligns with the application.yml settings.
#
# `vault secrets enable` fails if the engine is already mounted; we suppress
# that error to keep the script idempotent.
#
# CONTROL: RN-01 — enabling KV v2 engine; no secret values involved here.
# ---------------------------------------------------------------------------
echo "Step 1/4 — Enabling KV v2 engine at secret/ ..."
vault secrets enable -version=2 -path=secret kv 2>/dev/null \
  || echo "  [INFO] KV v2 already enabled at secret/ — skipping."
echo ""

# ---------------------------------------------------------------------------
# Step 2 — Seed secret/tmpl-rest-api/keycloak
#
# Path: secret/tmpl-rest-api/keycloak
# Key:  client-secret
#
# Used by: shared-identity — IdentitySecurityConfiguration reads this value
#   to configure the Keycloak client credential for the resource server.
#
# WARNING: DEV MODE ONLY — this is a placeholder; replace with a real
#   Keycloak client secret generated in the realm configuration for production.
#
# CONTROL: RN-01 — dev-placeholder-not-for-production; never a real credential.
# ---------------------------------------------------------------------------
echo "Step 2/4 — Seeding secret/tmpl-rest-api/keycloak ..."
vault kv put secret/tmpl-rest-api/keycloak \
  client-secret="dev-placeholder-not-for-production"
echo "  [OK] secret/tmpl-rest-api/keycloak written."
echo ""

# ---------------------------------------------------------------------------
# Step 3 — Seed secret/tmpl-rest-api/service
#
# Path: secret/tmpl-rest-api/service
# Key:  api-key
#
# Used by: tmpl-rest-api controllers — the api-key may be used for service-to-
#   service authentication in derived services built from this template.
#
# WARNING: DEV MODE ONLY — replace with a real, randomly-generated API key
#   in production, provisioned via Vault dynamic secrets or manual rotation.
#
# CONTROL: RN-01 — dev-placeholder-not-for-production; never a real credential.
# ---------------------------------------------------------------------------
echo "Step 3/4 — Seeding secret/tmpl-rest-api/service ..."
vault kv put secret/tmpl-rest-api/service \
  api-key="dev-placeholder-not-for-production"
echo "  [OK] secret/tmpl-rest-api/service written."
echo ""

# ---------------------------------------------------------------------------
# Step 4 — Seed secret/tmpl-rest-api/db
#
# Path: secret/tmpl-rest-api/db
# Key:  password
#
# This path is reserved for future use when a derived service adds a database.
# The tmpl-rest-api template itself does not use a database in Phase 1;
# this entry exists so that the Spring Cloud Vault import in application.yml
# does not fail at startup (the path must exist for the non-optional import).
#
# WARNING: DEV MODE ONLY — replace with real database credentials managed via
#   Vault dynamic secrets (database engine) in production.
#
# CONTROL: RN-01 — dev-placeholder-not-for-production; never a real credential.
# ---------------------------------------------------------------------------
echo "Step 4/4 — Seeding secret/tmpl-rest-api/db ..."
vault kv put secret/tmpl-rest-api/db \
  password="dev-placeholder-not-for-production"
echo "  [OK] secret/tmpl-rest-api/db written."
echo ""

# ---------------------------------------------------------------------------
# Verification — confirm all three paths are readable
# ---------------------------------------------------------------------------
echo "================================================================"
echo "  Verification — reading back seeded paths:"
echo "================================================================"
echo ""

echo "  secret/tmpl-rest-api/keycloak:"
vault kv get -field=client-secret secret/tmpl-rest-api/keycloak \
  | sed 's/^/    client-secret = /'
echo ""

echo "  secret/tmpl-rest-api/service:"
vault kv get -field=api-key secret/tmpl-rest-api/service \
  | sed 's/^/    api-key = /'
echo ""

echo "  secret/tmpl-rest-api/db:"
vault kv get -field=password secret/tmpl-rest-api/db \
  | sed 's/^/    password = /'
echo ""

echo "================================================================"
echo "  Vault seed complete."
echo ""
echo "  WARNING: DEV MODE ONLY — all values are placeholders."
echo "  Replace with real credentials before any non-local deployment."
echo "================================================================"
