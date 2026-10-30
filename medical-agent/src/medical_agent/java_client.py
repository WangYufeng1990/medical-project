"""Submitting results back to Java (Round 55.4).

The capability is read from the current call's context, never from the graph state, so it
cannot be written into a checkpoint.
"""

from __future__ import annotations

import contextvars
from dataclasses import dataclass

import httpx

from medical_agent.graph.state import RunState
from medical_agent.schemas.classification import Intent, RiskLevel
from medical_agent.schemas.result import CitationPayload, RunResultRequest, ValidationPayload


@dataclass(frozen=True)
class CallCredentials:
    capability: str
    java_base_url: str
    outbound_token: str


_credentials: contextvars.ContextVar[CallCredentials | None] = contextvars.ContextVar(
    "agent_call_credentials", default=None
)


def use_credentials(credentials: CallCredentials) -> contextvars.Token[CallCredentials | None]:
    return _credentials.set(credentials)


def reset_credentials(token: contextvars.Token[CallCredentials | None]) -> None:
    _credentials.reset(token)


def _strings(value: object) -> list[str]:
    return [str(item) for item in value] if isinstance(value, list) else []


def _dicts(value: object) -> list[dict[str, object]]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def submit_result(state: RunState) -> None:
    """POST the result for this attempt; a failure raises so the graph stops there."""
    credentials = _credentials.get()
    if credentials is None:
        raise RuntimeError("no call credentials are bound to this execution")
    raw_validation = state.get("validation")
    validation: dict[str, object] = raw_validation if isinstance(raw_validation, dict) else {}
    adopted = _dicts(state.get("adopted_citations"))
    body = RunResultRequest(
        run_id=state["run_id"],
        generation=state["execution_generation"],
        intent=Intent(state["classification"]),
        risk=RiskLevel(state["risk"]),
        requires_clinician=bool(state.get("requires_clinician")),
        draft=str(state["draft"]),
        citations=[
            CitationPayload(
                chunk_id=str(item["chunk_id"]),
                document_id=str(item["document_id"]),
                document_version=int(str(item["document_version"])),
                title=str(item["title"]),
                heading_path=_strings(item.get("heading_path")),
                content_hash=str(item["content_hash"]),
            )
            for item in adopted
        ],
        validation=ValidationPayload(
            ok=bool(validation.get("ok")),
            kind=str(validation.get("kind") or "unknown"),
            reasons=_strings(validation.get("reasons")),
            rejected_reasons=_strings(validation.get("rejected_reasons")),
        ),
    ).model_dump(mode="json")
    response = httpx.post(
        f"{credentials.java_base_url.rstrip('/')}/api/v1/internal/agent/runs/{state['run_id']}/results",
        json=body,
        headers={
            "X-Agent-Service-Token": credentials.outbound_token,
            "X-Agent-Capability": credentials.capability,
        },
        timeout=10.0,
    )
    response.raise_for_status()
