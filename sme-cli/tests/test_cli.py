"""Smoke tests for sme CLI entry point and --version flag (TASK-027).

Covers:
- AC INI-38: `sme --version` exits 0 and prints 'sme 0.1.0'.
- AC INI-37: `sme new <unknown-template> <name>` exits != 0 and lists
  'tmpl-rest-api' in the error output.
"""

import pytest
from click.testing import CliRunner

from sme.cli import main
from sme.templates import AVAILABLE_TEMPLATES


@pytest.fixture()
def runner() -> CliRunner:
    return CliRunner()


class TestVersionFlag:
    def test_version_exits_zero(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["--version"])
        assert result.exit_code == 0

    def test_version_output_format(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["--version"])
        assert "sme 0.1.0" in result.output


class TestNewCommandUnknownTemplate:
    def test_unknown_template_exits_nonzero(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["new", "template-inexistente", "foo"])
        assert result.exit_code != 0

    def test_unknown_template_lists_available(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["new", "template-inexistente", "foo"])
        # The error message must mention tmpl-rest-api
        combined_output = (result.output or "") + (result.stderr or "")
        assert "tmpl-rest-api" in combined_output

    def test_unknown_template_error_message_content(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["new", "template-inexistente", "foo"])
        combined_output = (result.output or "") + (result.stderr or "")
        assert "template-inexistente" in combined_output or "unknown" in combined_output.lower()


class TestAvailableTemplatesRegistry:
    def test_tmpl_rest_api_registered(self) -> None:
        assert "tmpl-rest-api" in AVAILABLE_TEMPLATES

    def test_tmpl_rest_api_has_path(self) -> None:
        assert "path" in AVAILABLE_TEMPLATES["tmpl-rest-api"]

    def test_tmpl_rest_api_has_description(self) -> None:
        assert "description" in AVAILABLE_TEMPLATES["tmpl-rest-api"]
        assert len(AVAILABLE_TEMPLATES["tmpl-rest-api"]["description"]) > 0
