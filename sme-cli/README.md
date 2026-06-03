# sme-cli

**SecureMicro Energy Templates CLI** — scaffold tool for creating new
microservices from the SecureMicro-Energy-Templates library.

**Status:** Phase 1 — implementado (scaffold, package rename, pom.xml update,
`--version`)

---

## Installation

```bash
cd sme-cli
pip install -e .
```

Requires Python 3.11+. After installation the `sme` command is available on
your PATH.

---

## Available Commands

### `sme --version`

Prints the CLI version and exits.

```
$ sme --version
sme 0.1.0
```

### `sme new <template-name> <project-name>`

Scaffolds a new microservice directory from the named template.

```
$ sme new tmpl-rest-api meu-servico
```

**Arguments:**

| Argument | Description |
|----------|-------------|
| `template-name` | Name of the template to use. Must be one of the registered templates (see below). |
| `project-name` | Name of the new service directory to create. Must contain only letters, digits and hyphens; must not already exist. |

**Available templates:**

| Template | Description |
|----------|-------------|
| `tmpl-rest-api` | Spring Boot 3 WebFlux REST API with OIDC (Keycloak), HashiCorp Vault, structured JSON logging, Micrometer metrics, OpenTelemetry tracing and CycloneDX SBOM. Production-grade baseline aligned to EO 14028 / NIST SP 800-53 / CISA. |

**Options (future — Phase 2):**

| Option | Description |
|--------|-------------|
| `--type rest-api\|batch-job\|event-driven` | Select template type (shorthand — Phase 2 alias when additional templates are registered) |

---

## Example

```
$ sme new tmpl-rest-api meu-servico
Scaffolding 'meu-servico' from template 'tmpl-rest-api'...
Replacing package 'com.securemicro.tmpl.restapi' -> 'com.securemicro.meu_servico' in source files...
Updating pom.xml artifact metadata...

Project 'meu-servico' created successfully.

Next steps (manual configuration required):

  1. Configure Keycloak client:
       - Create a new confidential client 'meu-servico' in your Keycloak realm.
       - Set Valid Redirect URIs to match your service URL.
       - Enable Standard Flow and Service Accounts Roles.
       - Note the client secret — you will store it in Vault (step 2).

  2. Configure Vault paths:
       - Store the Keycloak client secret at:
           secret/meu-servico/keycloak  (key: client-secret)
       - Store any service API keys at:
           secret/meu-servico/service   (key: api-key)

  3. Set environment variables before starting the service:
       KEYCLOAK_ISSUER_URI=https://<keycloak-host>/realms/<realm>
       VAULT_ADDR=https://<vault-host>
       VAULT_TOKEN=<your-token>  (use AppRole in production)

  4. Build and run:
       cd meu-servico
       mvn clean package
       java -jar target/meu-servico-*.jar
```

### Error: unknown template

If you provide a template name that does not exist, `sme new` exits with a
non-zero status and lists the available templates:

```
$ sme new unknown-template meu-servico
Error: unknown template 'unknown-template'.
Available templates: tmpl-rest-api
```

### Error: directory already exists

If the destination directory already exists, `sme new` aborts without
overwriting:

```
$ sme new tmpl-rest-api meu-servico
Error: directory 'meu-servico' already exists.
Aborting to avoid overwriting existing content.
```

---

## Package Renaming Rules

`sme new` replaces `com.securemicro.tmpl.restapi` with
`com.securemicro.<project-name>` in all `.java`, `.xml`, `.yml`, `.yaml`,
`.properties` and `.md` files inside the generated project.

Because Java identifiers cannot contain hyphens (JLS §3.8), hyphens in
`project-name` are converted to underscores in the package path only. The
directory name, `artifactId` and `<name>` in `pom.xml` keep the original
hyphen form.

| Input `project-name` | Java package | Directory / artifactId |
|----------------------|-------------|------------------------|
| `meu-servico` | `com.securemicro.meu_servico` | `meu-servico/` |
| `my-api-v2` | `com.securemicro.my_api_v2` | `my-api-v2/` |
| `myservice` | `com.securemicro.myservice` | `myservice/` |

---

## Development

### Setup

```bash
cd sme-cli
pip install -e ".[dev]"
```

If `[dev]` extras are not yet defined in `pyproject.toml`, install test
dependencies directly:

```bash
pip install pytest click
```

### Running tests

```bash
cd sme-cli
pytest
```

Tests are located in `sme-cli/tests/`. The suite covers:

- `test_cli.py` — `sme --version`, unknown template error (AC INI-37, INI-38)
- `test_new_command.py` — scaffold logic, package rename, pom.xml update,
  directory-exists guard, project-name validation (AC INI-35, INI-36)

### Lint

```bash
ruff check sme/ tests/
```

---

## Architecture

```
sme-cli/
├── pyproject.toml              # Package metadata and entry point
├── sme/
│   ├── __init__.py             # __version__ = "0.1.0"
│   ├── cli.py                  # Click entry point: main(), new_command()
│   ├── templates.py            # AVAILABLE_TEMPLATES registry
│   └── commands/
│       ├── __init__.py
│       └── new.py              # Scaffold logic: copy, rename, pom.xml update
└── tests/
    ├── test_cli.py
    └── test_new_command.py
```

The CLI is intentionally minimal for Phase 1. In Phase 2, automatic Keycloak
client registration and Vault path seeding are planned (currently these steps
are manual and documented in the post-generation output).

---

## Federal Alignment Notes

`sme new` enforces several security properties at generation time:

- Project names are validated against a strict regex to prevent path traversal
  and shell injection (NIST SI-10).
- The generated project contains no secrets — all sensitive values are
  referenced via Vault paths, never embedded in files (EO 14028 §4(e),
  NIST SC-12).
- Post-generation instructions guide the operator to store credentials in Vault
  and configure Keycloak, rather than using environment variables or plain-text
  config files.
