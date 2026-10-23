"""The service application.

Health endpoints (55.1) plus the run intake the durable dispatcher calls (55.4). The
endpoint authenticates with the inbound token the dispatcher presents, requires the
per-attempt capability, and hands the run to the worker; a result goes back to Java from
inside the graph, so an accepted call means the work really happened.
"""

from __future__ import annotations

import contextlib
import secrets
from collections.abc import AsyncIterator
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict

from medical_agent.checkpoints import open_checkpointer
from medical_agent.config import Settings
from medical_agent.java_client import CallCredentials
from medical_agent.kb.retrieval import Retriever
from medical_agent.kb.store import KnowledgeBase
from medical_agent.providers.base import Provider
from medical_agent.providers.factory import build_provider
from medical_agent.schemas.run import CONTRACT_VERSION, RunStartRequest
from medical_agent.worker import AgentWorker


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


def _require_inbound_token(settings: Settings, authorization: str | None) -> None:
    """Constant-time check; a missing or wrong token is 401, never a silent pass."""
    expected = settings.inbound_token.get_secret_value() if settings.inbound_token else ""
    presented = ""
    if authorization and authorization.startswith("Bearer "):
        presented = authorization.removeprefix("Bearer ")
    if not expected or not secrets.compare_digest(expected, presented):
        raise HTTPException(status_code=401, detail="invalid inbound credential")


def _open_retriever(settings: Settings) -> Retriever | None:
    """Load the approved corpus once, at startup; a broken one stops the service loudly."""
    manifest = Path(settings.kb_manifest_path)
    if not manifest.is_file():
        return None
    knowledge = KnowledgeBase(manifest)
    version = knowledge.ingest()
    if settings.kb_autopublish:
        knowledge.publish(version.id)
    # The callable, not a snapshot: a withdrawal must take effect without a restart.
    return Retriever(knowledge.chunks)


def create_app(
    settings: Settings,
    provider: Provider | None = None,
    worker: AgentWorker | None = None,
) -> FastAPI:
    """Build the application. Settings are validated by the caller (``load_settings``).

    The checkpointer owns a connection, so it is entered on startup and closed on shutdown:
    a saver obtained by dropping its context manager would already have lost the connection
    before ``setup()`` ran (Round 55.4 review).
    """
    selected = provider
    active_worker = worker
    stack: contextlib.ExitStack | None = None

    @contextlib.asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        nonlocal active_worker, stack
        stack = contextlib.ExitStack()
        try:
            if active_worker is None:
                active_provider = selected if selected is not None else build_provider(settings)
                checkpointer = stack.enter_context(open_checkpointer(settings.checkpoint_url))
                retriever = _open_retriever(settings)
                active_worker = AgentWorker(active_provider, checkpointer,
                                           max_prompt_chars=settings.max_prompt_chars,
                                           retriever=retriever)
                application.state.checkpointer = checkpointer
            application.state.worker = active_worker
            yield
        finally:
            if stack is not None:
                stack.close()
                stack = None

    app = FastAPI(title="medical-agent", version="0.1.0", lifespan=lifespan)

    @app.get("/health/live", response_model=LiveResponse)
    def live() -> LiveResponse:
        """Liveness: the process is running. Never depends on the provider."""
        return LiveResponse(status="ok", service=settings.service_name)

    @app.post("/internal/v1/runs", status_code=202)
    def start_run(
        request: RunStartRequest,
        authorization: str | None = Header(default=None),
        capability: str | None = Header(default=None, alias="X-Agent-Capability"),
    ) -> dict[str, object]:
        """Accept one dispatch. Validation happens before any model work."""
        _require_inbound_token(settings, authorization)
        if not capability:
            raise HTTPException(status_code=400, detail="X-Agent-Capability is required")

        worker_for_call = getattr(app.state, "worker", None)
        if worker_for_call is None:
            raise HTTPException(status_code=503, detail="the worker is not started")
        credentials = CallCredentials(
            capability=capability,
            java_base_url=settings.java_base_url,
            outbound_token=settings.outbound_token.get_secret_value()
            if settings.outbound_token
            else "",
        )
        worker_for_call.process(request, credentials)
        return {"run_id": request.run_id, "status": "ACCEPTED"}

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
        except Exception as exc:  # a broken provider must not look ready
            raise HTTPException(status_code=503,
                                detail=f"the configured provider cannot be built: {exc}") from exc
        return ReadyResponse(
            status="ready",
            service=settings.service_name,
            provider=active.name,
            contract_version=CONTRACT_VERSION,
        )

    return app
