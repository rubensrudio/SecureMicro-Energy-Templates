import sys

import click

from sme import __version__
from sme.templates import AVAILABLE_TEMPLATES


def _list_available_templates() -> str:
    """Return a formatted string listing all available templates."""
    names = ", ".join(AVAILABLE_TEMPLATES.keys())
    return names


@click.group()
@click.version_option(
    version=__version__,
    prog_name="sme",
    message="%(prog)s %(version)s",
)
def main() -> None:
    """SecureMicro Energy Templates CLI.

    Use 'sme new <template> <project-name>' to scaffold a new project from
    one of the available templates.
    """


@main.command("new")
@click.argument("template_name")
@click.argument("project_name")
def new_command(template_name: str, project_name: str) -> None:
    """Scaffold a new project from TEMPLATE_NAME into directory PROJECT_NAME.

    TEMPLATE_NAME must be one of the registered templates (see 'sme new --help').
    PROJECT_NAME is the directory that will be created for the new project.
    """
    if template_name not in AVAILABLE_TEMPLATES:
        available = _list_available_templates()
        click.echo(
            f"Error: unknown template '{template_name}'.\n"
            f"Available templates: {available}",
            err=True,
        )
        sys.exit(1)

    # Delegate to the commands.new module which handles the actual scaffold
    # logic (copy, package rename, pom.xml update).  That module is
    # implemented in TASK-028; calling it here ensures the entry point wiring
    # is correct without duplicating scaffold logic in cli.py.
    from sme.commands import new as new_module  # noqa: PLC0415 — deferred import

    new_module.run(template_name=template_name, project_name=project_name)
