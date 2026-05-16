import click
from sme import __version__


@click.group()
@click.version_option(version=__version__)
def main():
    """SecureMicro Energy Templates CLI."""
    pass
