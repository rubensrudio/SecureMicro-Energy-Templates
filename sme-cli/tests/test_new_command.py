"""Tests for sme new command — scaffold and package rename (TASK-028).

Covers:
- AC INI-35: creates <project-name>/ directory from tmpl-rest-api template.
- AC INI-36: renames Java package and updates artifactId / <name> in pom.xml.
- Edge case (spec): aborts with exit code != 0 when destination directory
  already exists; existing content must NOT be modified.
- Security: project-name validation rejects path traversal and shell injection.
"""

from __future__ import annotations

import os
import textwrap
from pathlib import Path
from typing import Iterator

import pytest
from click.testing import CliRunner

from sme.cli import main
from sme.commands.new import (
    PROJECT_NAME_RE,
    _replace_in_file,
    _resolve_template_source,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture()
def runner() -> CliRunner:
    return CliRunner()


@pytest.fixture()
def tmp_project_dir(tmp_path: Path) -> Iterator[Path]:
    """Change working directory to a temp folder so that sme new creates
    the project there and not inside the repository tree."""
    original = Path.cwd()
    os.chdir(tmp_path)
    yield tmp_path
    os.chdir(original)


@pytest.fixture()
def minimal_template(tmp_path: Path) -> Path:
    """Build a minimal fake template directory with the same structure that
    the real tmpl-rest-api uses so tests are not coupled to a full Maven build.
    """
    tmpl = tmp_path / "fake-tmpl-rest-api"
    tmpl.mkdir()

    # pom.xml with the tokens that new.py must replace
    pom = tmpl / "pom.xml"
    pom.write_text(
        textwrap.dedent(
            """\
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <artifactId>tmpl-rest-api</artifactId>
                <name>SecureMicro :: Template REST API</name>
            </project>
            """
        ),
        encoding="utf-8",
    )

    # Java source file with the old package declaration
    java_dir = (
        tmpl / "src" / "main" / "java" / "com" / "securemicro" / "tmpl" / "restapi"
    )
    java_dir.mkdir(parents=True)
    java_src = java_dir / "Application.java"
    java_src.write_text(
        "package com.securemicro.tmpl.restapi;\n\npublic class Application {}\n",
        encoding="utf-8",
    )

    # application.yml with a reference to the old package
    config_dir = tmpl / "config"
    config_dir.mkdir()
    yml = config_dir / "application.yml"
    yml.write_text(
        "spring:\n  application:\n    name: com.securemicro.tmpl.restapi\n",
        encoding="utf-8",
    )

    # Binary file that must NOT be touched
    binary = tmpl / "some.class"
    binary.write_bytes(b"\xca\xfe\xba\xbe\x00\x00\x00\x3d")

    return tmpl


# ---------------------------------------------------------------------------
# Unit tests for helpers
# ---------------------------------------------------------------------------


class TestProjectNameValidation:
    """PROJECT_NAME_RE must reject unsafe names."""

    @pytest.mark.parametrize(
        "name",
        [
            "meu-servico",
            "my-service",
            "service123",
            "a",
            "abc-def-ghi",
            "ABC-XYZ",
        ],
    )
    def test_valid_names_match(self, name: str) -> None:
        assert PROJECT_NAME_RE.fullmatch(name) is not None, (
            f"Expected '{name}' to be a valid project name"
        )

    @pytest.mark.parametrize(
        "name",
        [
            "",
            "../evil",
            "../../etc/passwd",
            "foo/bar",
            "foo bar",
            "foo;bar",
            "foo&bar",
            "foo|bar",
            "foo$bar",
            "foo`bar",
            "foo\nbar",
            "foo.bar",
            "_underscore",
        ],
    )
    def test_invalid_names_do_not_match(self, name: str) -> None:
        assert PROJECT_NAME_RE.fullmatch(name) is None, (
            f"Expected '{name}' to be rejected"
        )


class TestReplaceInFile:
    """_replace_in_file must perform text substitution and skip binaries."""

    def test_replaces_token_in_text_file(self, tmp_path: Path) -> None:
        f = tmp_path / "Sample.java"
        f.write_text("package com.securemicro.tmpl.restapi;\n", encoding="utf-8")
        _replace_in_file(
            f, "com.securemicro.tmpl.restapi", "com.securemicro.my-service"
        )
        assert "com.securemicro.my-service" in f.read_text(encoding="utf-8")
        assert "com.securemicro.tmpl.restapi" not in f.read_text(encoding="utf-8")

    def test_skips_binary_file(self, tmp_path: Path) -> None:
        f = tmp_path / "app.class"
        original_bytes = b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"
        f.write_bytes(original_bytes)
        _replace_in_file(f, "tmpl.restapi", "my-service")
        # Binary content must be untouched
        assert f.read_bytes() == original_bytes

    def test_no_match_leaves_file_unchanged(self, tmp_path: Path) -> None:
        f = tmp_path / "noop.xml"
        original = "<artifactId>something-else</artifactId>\n"
        f.write_text(original, encoding="utf-8")
        _replace_in_file(f, "tmpl.restapi", "my-service")
        assert f.read_text(encoding="utf-8") == original


class TestResolveTemplateSource:
    """_resolve_template_source must return the absolute path from AVAILABLE_TEMPLATES."""

    def test_resolves_existing_template(self) -> None:
        src = _resolve_template_source("tmpl-rest-api")
        assert src.is_absolute()

    def test_raises_for_unknown_template(self) -> None:
        with pytest.raises(KeyError):
            _resolve_template_source("non-existent-template")


# ---------------------------------------------------------------------------
# Integration tests via CLI runner
# ---------------------------------------------------------------------------


class TestNewCommandSuccess:
    """Happy-path: sme new creates project and renames artifacts."""

    def test_creates_destination_directory(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        result = runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        assert result.exit_code == 0, result.output
        assert (tmp_project_dir / "meu-servico").is_dir()

    def test_pom_artifactid_updated(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        pom_text = (tmp_project_dir / "meu-servico" / "pom.xml").read_text(
            encoding="utf-8"
        )
        assert "<artifactId>meu-servico</artifactId>" in pom_text
        assert "tmpl-rest-api" not in pom_text

    def test_pom_name_updated(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        pom_text = (tmp_project_dir / "meu-servico" / "pom.xml").read_text(
            encoding="utf-8"
        )
        assert "<name>meu-servico</name>" in pom_text

    def test_java_package_renamed(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        java_files = list((tmp_project_dir / "meu-servico").rglob("*.java"))
        assert java_files, "Expected at least one .java file in the generated project"
        for jf in java_files:
            content = jf.read_text(encoding="utf-8")
            assert "com.securemicro.tmpl.restapi" not in content, (
                f"Old package still present in {jf}"
            )

    def test_no_old_package_string_anywhere_in_text_files(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        project_dir = tmp_project_dir / "meu-servico"
        OLD_PACKAGE = "com.securemicro.tmpl.restapi"
        for candidate in project_dir.rglob("*"):
            if not candidate.is_file():
                continue
            try:
                text = candidate.read_text(encoding="utf-8")
                assert OLD_PACKAGE not in text, (
                    f"Old package string found in {candidate}"
                )
            except (UnicodeDecodeError, PermissionError):
                # Binary file — expected to be skipped
                pass

    def test_binary_files_not_corrupted(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        class_file = tmp_project_dir / "meu-servico" / "some.class"
        assert class_file.exists()
        assert class_file.read_bytes() == b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"

    def test_post_generation_instructions_displayed(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Output must mention Keycloak and Vault post-generation steps (P-06)."""
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        result = runner.invoke(main, ["new", "tmpl-rest-api", "meu-servico"])
        assert result.exit_code == 0
        combined = result.output.lower()
        assert "keycloak" in combined
        assert "vault" in combined


class TestNewCommandExistingDirectory:
    """Edge case: destination directory already exists — abort, do not modify."""

    def test_exits_nonzero_when_dir_exists(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        (tmp_project_dir / "already-exists").mkdir()
        result = runner.invoke(main, ["new", "tmpl-rest-api", "already-exists"])
        assert result.exit_code != 0

    def test_existing_dir_content_not_modified(
        self,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        existing = tmp_project_dir / "already-exists"
        existing.mkdir()
        sentinel = existing / "sentinel.txt"
        sentinel.write_text("original content", encoding="utf-8")

        runner.invoke(main, ["new", "tmpl-rest-api", "already-exists"])

        # Sentinel must be intact
        assert sentinel.read_text(encoding="utf-8") == "original content"
        # No extra files should have been copied into the existing dir
        assert list(existing.iterdir()) == [sentinel]


class TestNewCommandInvalidProjectName:
    """Security: invalid project-name must be rejected before any file I/O."""

    @pytest.mark.parametrize(
        "bad_name",
        [
            "../evil",
            "../../etc/passwd",
            "foo/bar",
            "foo bar",
            "foo;bar",
            "foo$bar",
            "foo.bar",
        ],
    )
    def test_invalid_name_exits_nonzero(
        self,
        bad_name: str,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        result = runner.invoke(main, ["new", "tmpl-rest-api", bad_name])
        assert result.exit_code != 0

    @pytest.mark.parametrize(
        "bad_name",
        [
            "../evil",
            "foo/bar",
        ],
    )
    def test_invalid_name_does_not_create_directory(
        self,
        bad_name: str,
        runner: CliRunner,
        minimal_template: Path,
        tmp_project_dir: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.setattr(
            "sme.commands.new._resolve_template_source",
            lambda _name: minimal_template,
        )
        # Snapshot directories present BEFORE invocation (minimal_template may
        # be a sibling under the same pytest tmp root).
        dirs_before = set(tmp_project_dir.iterdir())

        runner.invoke(main, ["new", "tmpl-rest-api", bad_name])

        # No NEW directory should have been created inside tmp_project_dir.
        dirs_after = set(tmp_project_dir.iterdir())
        new_dirs = dirs_after - dirs_before
        assert not new_dirs, (
            f"Unexpected new entries created for bad name '{bad_name}': {new_dirs}"
        )
