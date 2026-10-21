"""The service application (Round 55.1).

Only health endpoints exist at this point. The run endpoints arrive with the durable
dispatcher in 55.2; shipping them now as stubs that accept work and do nothing would
be a lie the Java layer could not see.
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict

from medical_agent.config import Settings
from medical_agent.providers.base import Provider
from medical_agent.providers.factory import build_provider
from medical_agent.schemas.run import CONTRACT_VERSION


class LiveResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    status: str
    service: str


class ReadyResponse(BaseModel):
    """Readiness: the service can do its job, not merely that the process is up.

    The provider name is part of the answer so an operator can tell a mock deployment
    from a real one without reading configuration files.
    """

    model_config = ConfigDict(extra="forbid", frozen=True)

    status: str
    service: str
    provider: str
    contract_version: str


def create_app(settings: Settings, provider: Provider | None = None) -> FastAPI:
    """Build the application. Settings are validated by the caller (``load_settings``)."""
    app = FastAPI(title="medical-agent", version="0.1.0")
    selected = provider

    @app.get("/health/live", response_model=LiveResponse)
    def live() -> LiveResponse:
        """Liveness: the process is running. Never depends on the provider."""
        return LiveResponse(status="ok", service=settings.service_name)

    @app.get("/health/ready", response_model=ReadyResponse, responses={503: {"model": dict}})
    def ready() -> ReadyResponse:
        """Readiness: the configured provider can be constructed.

        A failure here is reported as 503, not swallowed; the Java layer keeps working
        on its own and the health of the agent never gates human messaging.
        """
        try:
            active = selected if selected is not None else build_provider(settings)
        except NotImplementedError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        return ReadyResponse(
            status="ready",
            service=settings.service_name,
            provider=active.name,
            contract_version=CONTRACT_VERSION,
        )

    return app
