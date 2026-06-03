# Demo

Two demos: a **CLI scaffold demo** (runs anywhere, no Docker) and a **full-stack
demo** (needs the `reference-deploy` Docker stack).

---

## Demo A — scaffold a service in 30 seconds (no Docker)

Proves the core value prop: a compliant, buildable service from one command.

```bash
# from repo root
cd sme-cli
pip install -e .
sme --version            # -> sme 0.1.0
sme new tmpl-rest-api payments-svc
```

Expected output:

```
Scaffolding 'payments-svc' from template 'tmpl-rest-api'...
Replacing package 'com.securemicro.tmpl.restapi' -> 'com.securemicro.payments_svc' in source files...
Updating pom.xml artifact metadata...
Project 'payments-svc' created successfully.
```

Then build the generated service:

```bash
cd ../payments-svc
mvn clean verify -DskipTests   # compiles + generates CycloneDX SBOM
```

### Record it with asciinema

```bash
asciinema rec docs/demo/scaffold-demo.cast \
  --title "SecureMicro — scaffold + build" --idle-time-limit 2
# run the commands above, then Ctrl-D to stop
```

Embed in the README via the asciinema badge, or convert to GIF:

```bash
agg docs/demo/scaffold-demo.cast docs/demo/scaffold-demo.gif
```

Commit the `.cast` (small, text) — prefer it over the GIF.

---

## Demo B — full local stack (Keycloak + Vault + service + Jaeger)

Needs Docker 24+. Walks the auth → audit → trace path end to end.

```bash
cd reference-deploy
docker compose up -d
```

Demo script (each step has a matching screenshot in
[`../screenshots/`](../screenshots/README.md)):

```bash
# 1. health
curl -s localhost:8080/actuator/health | jq

# 2. unauthenticated -> 401
curl -i localhost:8080/api/v1/resources

# 3. get a token from Keycloak (dev realm)
TOKEN=$(curl -s localhost:8081/realms/securemicro/protocol/openid-connect/token \
  -d grant_type=password -d client_id=demo-cli \
  -d username=demo -d password=demo | jq -r .access_token)

# 4. authorized -> 200 + AUDIT log line emitted
curl -i -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/resources

# 5. metrics
curl -s localhost:8080/actuator/prometheus | grep http_server_requests | head

# 6. open the trace
open http://localhost:16686   # Jaeger UI
```

Full step-by-step (with realm/Vault setup details):
[`../quickstart/tmpl-rest-api-quickstart.md`](../quickstart/tmpl-rest-api-quickstart.md).

> **Dev only.** This stack runs Vault in dev mode and a fixed Keycloak admin
> password. Never use this configuration in production.
