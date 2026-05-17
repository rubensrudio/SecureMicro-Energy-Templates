# Registry of available SME templates.
# Used by the `sme new` command to validate template names and display
# available options when an unknown template is requested (AC INI-37).

AVAILABLE_TEMPLATES: dict[str, dict[str, str]] = {
    "tmpl-rest-api": {
        "path": "tmpl-rest-api",
        "description": (
            "Spring Boot 3 WebFlux REST API with OIDC (Keycloak), "
            "HashiCorp Vault secrets management, structured JSON logging, "
            "Micrometer metrics, OpenTelemetry tracing and CycloneDX SBOM. "
            "Production-grade baseline aligned to EO 14028 / NIST SP 800-53 / CISA."
        ),
    },
}
