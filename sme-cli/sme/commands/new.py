"""sme new command — scaffold and package rename (TASK-028).

Implements AC INI-35 and AC INI-36:
  1. Validate that the template exists in the AVAILABLE_TEMPLATES registry.
  2. Validate that the destination directory does NOT exist — abort without
     overwriting (edge case defined in spec).
  3. Validate that project-name contains only safe characters (alphanumeric
     and hyphens) to prevent path traversal and shell injection (security
     requirement in TASK-028 description).
  4. Copy the template source tree to ./<project-name>/.
  5. Recursively replace 'com.securemicro.tmpl.restapi' with
     'com.securemicro.<project-name>' in all .java, .xml, .yml / .yaml files.
  6. Update <artifactId> and <name> in pom.xml to <project-name>.
  7. Display manual post-generation instructions for Keycloak client
     configuration and Vault paths (Premissa P-06).
"""

from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

import click

from sme.templates import AVAILABLE_TEMPLATES

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# Tokens in the template source that must be replaced after copying.
OLD_JAVA_PACKAGE: str = "com.securemicro.tmpl.restapi"
OLD_ARTIFACT_ID: str = "tmpl-rest-api"
OLD_NAME_TAG: str = "SecureMicro :: Template REST API"

# File extensions processed for text substitution.
_TEXT_EXTENSIONS: frozenset[str] = frozenset(
    {".java", ".xml", ".yml", ".yaml", ".properties", ".md"}
)

# ---------------------------------------------------------------------------
# Project-name validation
# ---------------------------------------------------------------------------

# Only lowercase/uppercase letters, digits and hyphens are permitted.
# This prevents path traversal (no '/', '..'), shell injection (no ';', '$',
# '`', '|', '&', etc.) and other special characters.
PROJECT_NAME_RE: re.Pattern[str] = re.compile(r"^[A-Za-z0-9][A-Za-z0-9\-]*$")


def _validate_project_name(project_name: str) -> None:
    """Raise SystemExit with an informative message for unsafe project names."""
    if not PROJECT_NAME_RE.fullmatch(project_name):
        click.echo(
            f"Error: invalid project name '{project_name}'.\n"
            "Project names must start with a letter or digit and may only "
            "contain letters, digits and hyphens (no spaces, slashes, dots "
            "or special characters).",
            err=True,
        )
        sys.exit(1)


# ---------------------------------------------------------------------------
# Template source resolution
# ---------------------------------------------------------------------------


def _resolve_template_source(template_name: str) -> Path:
    """Return the absolute Path to the template source directory.

    The path stored in AVAILABLE_TEMPLATES is relative to the repository root
    (i.e., relative to the parent directory of the sme-cli/ folder).  We
    resolve it relative to this module's location so that the CLI works
    regardless of the current working directory.

    Raises:
        KeyError: when template_name is not in AVAILABLE_TEMPLATES.
    """
    relative_path: str = AVAILABLE_TEMPLATES[template_name]["path"]
    # __file__ is sme-cli/sme/commands/new.py
    # parent        → sme-cli/sme/commands/
    # parent.parent → sme-cli/sme/
    # parent * 3    → sme-cli/
    # parent * 4    → repository root
    repo_root: Path = Path(__file__).resolve().parent.parent.parent.parent
    return (repo_root / relative_path).resolve()


# ---------------------------------------------------------------------------
# File-level text replacement
# ---------------------------------------------------------------------------


def _is_binary(path: Path) -> bool:
    """Return True if the file appears to be binary (non-UTF-8 decodable)."""
    if path.suffix.lower() not in _TEXT_EXTENSIONS:
        # Fast path: skip extensions we never process.
        return True
    try:
        path.read_text(encoding="utf-8")
        return False
    except (UnicodeDecodeError, PermissionError):
        return True


def _replace_in_file(path: Path, old: str, new: str) -> None:
    """Replace all occurrences of *old* with *new* in a text file.

    Binary files and files whose extension is not in _TEXT_EXTENSIONS are
    silently skipped to avoid corrupting compiled artefacts (.class, .jar).
    """
    if _is_binary(path):
        return
    original = path.read_text(encoding="utf-8")
    if old not in original:
        return
    updated = original.replace(old, new)
    path.write_text(updated, encoding="utf-8")


# ---------------------------------------------------------------------------
# pom.xml targeted updates
# ---------------------------------------------------------------------------


def _update_pom(pom_path: Path, project_name: str) -> None:
    """Update <artifactId> and <name> in pom.xml for the generated project.

    Uses simple string replacement rather than an XML parser to avoid
    introducing extra dependencies.  The tokens replaced are:
      - The first <artifactId>tmpl-rest-api</artifactId> occurrence.
      - The first <name>SecureMicro :: Template REST API</name> occurrence.

    The Java package substitution (`com.securemicro.tmpl.restapi`) is handled
    by the general `_replace_in_file` step that runs over ALL .xml files, so
    this function focuses only on the pom.xml-specific artifact metadata.
    """
    if not pom_path.exists():
        return

    text = pom_path.read_text(encoding="utf-8")

    # Replace <artifactId>tmpl-rest-api</artifactId>
    text = text.replace(
        f"<artifactId>{OLD_ARTIFACT_ID}</artifactId>",
        f"<artifactId>{project_name}</artifactId>",
    )

    # Replace <name>SecureMicro :: Template REST API</name>
    text = text.replace(
        f"<name>{OLD_NAME_TAG}</name>",
        f"<name>{project_name}</name>",
    )

    pom_path.write_text(text, encoding="utf-8")


# ---------------------------------------------------------------------------
# Post-generation instructions
# ---------------------------------------------------------------------------

_POST_GEN_INSTRUCTIONS = """\

Project '{name}' created successfully.

Next steps (manual configuration required — see Premissa P-06):

  1. Configure Keycloak client:
       - Create a new confidential client '{name}' in your Keycloak realm.
       - Set Valid Redirect URIs to match your service URL.
       - Enable Standard Flow and Service Accounts Roles.
       - Note the client secret — you will store it in Vault (step 2).

  2. Configure Vault paths:
       - Store the Keycloak client secret at:
           secret/{name}/keycloak  (key: client-secret)
       - Store any service API keys at:
           secret/{name}/service   (key: api-key)
       - Store database credentials at:
           secret/{name}/db        (keys: username, password)
       - Reference: {name}/config/vault-paths.md (if present in template)

  3. Set environment variables before starting the service:
       KEYCLOAK_ISSUER_URI=https://<keycloak-host>/realms/<realm>
       VAULT_ADDR=https://<vault-host>
       VAULT_TOKEN=<your-token>  (use AppRole in production)

  4. Build and run:
       cd {name}
       mvn clean package
       java -jar target/{name}-*.jar
"""


def _print_post_gen_instructions(project_name: str) -> None:
    click.echo(_POST_GEN_INSTRUCTIONS.format(name=project_name))


# ---------------------------------------------------------------------------
# Main entry point (called by cli.py)
# ---------------------------------------------------------------------------


def run(template_name: str, project_name: str) -> None:
    """Execute the scaffold workflow for `sme new`.

    Steps:
        1. Validate project_name (security gate — must be first).
        2. Check destination directory does not exist.
        3. Resolve template source path.
        4. Copy template tree to ./<project_name>/.
        5. Replace Java package string in all eligible text files.
        6. Update artifactId and <name> in pom.xml.
        7. Print post-generation instructions.
    """
    # --- Step 1: validate project name (security) ---
    _validate_project_name(project_name)

    # --- Step 2: destination must not exist ---
    destination = Path.cwd() / project_name
    if destination.exists():
        click.echo(
            f"Error: directory '{project_name}' already exists.\n"
            "Aborting to avoid overwriting existing content.",
            err=True,
        )
        sys.exit(1)

    # --- Step 3: resolve template source ---
    source = _resolve_template_source(template_name)
    if not source.exists():
        click.echo(
            f"Error: template source directory not found at '{source}'.\n"
            "Ensure the repository is fully cloned and the template exists.",
            err=True,
        )
        sys.exit(1)

    # --- Step 4: copy template tree ---
    click.echo(f"Scaffolding '{project_name}' from template '{template_name}'...")
    shutil.copytree(str(source), str(destination))

    # --- Step 5: replace Java package in text files ---
    new_package = f"com.securemicro.{project_name}"
    click.echo(
        f"Replacing package '{OLD_JAVA_PACKAGE}' -> '{new_package}' in source files..."
    )
    for file_path in destination.rglob("*"):
        if file_path.is_file():
            _replace_in_file(file_path, OLD_JAVA_PACKAGE, new_package)

    # --- Step 6: update pom.xml artifact metadata ---
    pom_path = destination / "pom.xml"
    if pom_path.exists():
        click.echo("Updating pom.xml artifact metadata...")
        _update_pom(pom_path, project_name)

    # --- Step 7: post-generation instructions ---
    _print_post_gen_instructions(project_name)
