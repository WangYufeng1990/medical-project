"""Submitting results back to Java (Round 55.4).

The capability is read from the current call's context, never from the graph state, so it
cannot be written into a checkpoint.
"""

from __future__ import annotations

import contextvars
from dataclasses import dataclass

import httpx

from medical_agent.graph.state import RunState


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


def outcome_for(state: RunState) -> str:
    """The provisional result string: routing decision, then the fact that a draft exists."""
    return f"{state['classification']}:{state['risk']}:DRAFT_READY"


def submit_result(state: RunState) -> None:
    """POST the result for this attempt; a failure raises so the graph stops there."""
    credentials = _credentials.get()
    if credentials is None:
        raise RuntimeError("no call credentials are bound to this execution")
    body = {
        "generation": state["execution_generation"],
        "outcome": outcome_for(state),
    }
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
