"""Submitting results back to Java (Round 55.4).

The capability is read from the current call's context, never from the graph state, so it
cannot be written into a checkpoint.
"""

from __future__ import annotations

import contextvars
from dataclasses import dataclass
from typing import Literal

import httpx

from medical_agent.graph.state import RunState
from medical_agent.schemas.classification import Intent, RiskLevel
from medical_agent.schemas.result import (
    CitationPayload,
    RunResultRequest,
    ToolFact,
    ToolPayload,
    ValidationPayload,
)


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


@dataclass(frozen=True)
class RunReceipt:
    """What Java says about a generation's result (Round 57.3)."""

    run_id: int
    generation: int | None
    state: str
    has_result: bool
    accepted: bool


def fetch_receipt(run_id: int, generation: int | None) -> RunReceipt | None:
    """The receipt, or None when Java cannot answer (old build, outage)."""
    credentials = _credentials.get()
    if credentials is None:
        return None
    try:
        response = httpx.get(
            f"{credentials.java_base_url.rstrip('/')}/api/v1/internal/agent/runs/{run_id}/receipt",
            params={"generation": generation},
            headers={"X-Agent-Service-Token": credentials.outbound_token},
            timeout=5.0,
        )
    except httpx.HTTPError:
        return None
    if response.status_code != 200:
        return None
    body = response.json().get("data") or {}
    body_generation = body.get("generation")
    return RunReceipt(
        run_id=int(body.get("runId") or run_id),
        generation=int(body_generation) if body_generation is not None else None,
        state=str(body.get("state") or ""),
        has_result=bool(body.get("hasResult")),
        accepted=bool(body.get("accepted")),
    )


ToolReason = Literal["tool_failed", "deadline_exceeded", "call_budget_exhausted",
                     "tool_unavailable"]

_KNOWN_TOOL_REASONS: dict[str, ToolReason] = {
    "tool_failed": "tool_failed",
    "deadline_exceeded": "deadline_exceeded",
    "call_budget_exhausted": "call_budget_exhausted",
    "tool_unavailable": "tool_unavailable",
}


def _tool_payload(state: RunState) -> ToolPayload | None:
    if state.get("tool_needs_human"):
        # The contract carries a fixed code; an unexpected value must not become free text.
        reason = _KNOWN_TOOL_REASONS.get(str(state.get("tool_reason") or ""), "tool_unavailable")
        return ToolPayload(name="get_my_appointments", status="needs_human",
                           reason=reason, facts=[])
    facts = _tool_facts(state.get("tool_result"))
    if facts is None:
        return None
    return ToolPayload(name="get_my_appointments", status="ok", facts=facts)


def _tool_facts(value: object) -> list[ToolFact] | None:
    if not isinstance(value, list):
        return None
    facts: list[ToolFact] = []
    for item in value:
        facts.append(ToolFact(
            appointment_id=int(getattr(item, "appointmentId", 0)),
            status=int(getattr(item, "status", 0)),
            start_time=str(getattr(item, "startTime", "")),
            timezone=str(getattr(item, "timezone", "")),
            duration_minutes=getattr(item, "durationMinutes", None),
            provider_name=getattr(item, "providerName", None),
            department=getattr(item, "department", None),
        ))
    return facts


def submit_result(state: RunState) -> None:
    """POST the result for this attempt; a failure raises so the graph stops there."""
    credentials = _credentials.get()
    if credentials is None:
        raise RuntimeError("no call credentials are bound to this execution")
    generation = int(state["execution_generation"])
    receipt = fetch_receipt(int(state["run_id"]), generation)
    if (receipt is not None and receipt.accepted and receipt.run_id == int(state["run_id"])
            and receipt.generation == generation):
        # Recovery must not resend: the status codes cannot say "already accepted", the receipt can.
        # Java's answer is about one generation, so a receipt for another one proves nothing.
        return
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
        tool=_tool_payload(state),
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
