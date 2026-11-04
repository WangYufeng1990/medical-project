"""The HTTP client the guarded tools call (Round 58.2).

It uses the run's credentials, so the same capability that authorizes the run authorizes its reads;
a refusal or transport failure becomes `tool_failed`, which the executor turns into a human task.
"""

from __future__ import annotations

import httpx

from medical_agent.java_client import _credentials
from medical_agent.tools.registry import ToolDenied


def java_reader(base_url: str, timeout: float = 5.0):  # type: ignore[no-untyped-def]
    """Builds a client for a run: `GET base_url + path` with the run's capability and token."""

    def read(path: str) -> object:
        credentials = _credentials.get()
        if credentials is None:
            raise ToolDenied("tool_failed", "no credentials")
        response = httpx.get(
            f"{base_url.rstrip('/')}{path}",
            headers={
                "X-Agent-Service-Token": credentials.outbound_token,
                "X-Agent-Capability": credentials.capability,
            },
            timeout=timeout,
        )
        if response.status_code != 200:
            raise ToolDenied("tool_failed", f"status {response.status_code}")
        return response.json().get("data")

    return read
