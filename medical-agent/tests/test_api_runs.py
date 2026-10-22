"""Round 55.4 — the run intake the Java dispatcher calls.

The Java side is a local HTTP server here, so the test sees the result submission exactly as
the business layer will: header names, credentials and body.
"""

from __future__ import annotations

import json
import threading
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import ClassVar

import pytest
from fastapi.testclient import TestClient

from medical_agent.api.app import create_app
from medical_agent.checkpoints import open_checkpointer
from medical_agent.config import Settings
from medical_agent.providers.base import ProviderRequest, ProviderResponse
from medical_agent.worker import AgentWorker

INBOUND = "inbound-token-for-tests"
OUTBOUND = "outbound-token-for-tests"
CAPABILITY = "capability-for-tests"


class StubJava(BaseHTTPRequestHandler):
    received: ClassVar[list[dict[str, object]]] = []

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length))
        StubJava.received.append(
            {
                "path": self.path,
                "service_token": self.headers.get("X-Agent-Service-Token"),
                "capability": self.headers.get("X-Agent-Capability"),
                "body": body,
            }
        )
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"code":200,"message":"ok"}')

    def log_message(self, format: str, *args: object) -> None:
        pass


class StubProvider:
    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        return ProviderResponse(text="mock", provider="mock", model="mock-1", latency_ms=1)


@pytest.fixture()
def java_stub() -> Iterator[str]:
    StubJava.received = []
    server = ThreadingHTTPServer(("127.0.0.1", 0), StubJava)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


def build_client(tmp_path: Path, java_url: str) -> TestClient:
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_url,
        checkpoint_url=f"sqlite://{tmp_path / 'api.sqlite'}",
    )
    worker = AgentWorker(StubProvider(), open_checkpointer(settings.checkpoint_url))
    return TestClient(create_app(settings, provider=StubProvider(), worker=worker))


def request_body(**overrides: object) -> dict[str, object]:
    body: dict[str, object] = {
        "contract_version": "v1",
        "run_id": 41,
        "message_id": 9001,
        "patient_id": 100,
        "conversation_revision": 12,
        "sender_type": "PATIENT",
        "execution_generation": 1,
        "content": "Can I reschedule my appointment?",
        "locale": "en",
        "request_id": "req-0123456789",
    }
    body.update(overrides)
    return body


def headers(token: str = INBOUND, capability: str | None = CAPABILITY) -> dict[str, str]:
    result = {"Authorization": f"Bearer {token}"}
    if capability is not None:
        result["X-Agent-Capability"] = capability
    return result


def test_an_authenticated_dispatch_runs_the_graph_and_submits_the_result(
    tmp_path, java_stub
) -> None:
    client = build_client(tmp_path, java_stub)

    response = client.post("/internal/v1/runs", json=request_body(), headers=headers())

    assert response.status_code == 202
    assert response.json()["run_id"] == 41
    assert len(StubJava.received) == 1
    sent = StubJava.received[0]
    assert sent["path"] == "/api/v1/internal/agent/runs/41/results"
    assert sent["service_token"] == OUTBOUND
    assert sent["capability"] == CAPABILITY
    assert sent["body"] == {"generation": 1, "outcome": "APPOINTMENT:DRAFT_READY"}


def test_a_wrong_inbound_token_is_refused(tmp_path, java_stub) -> None:
    client = build_client(tmp_path, java_stub)

    response = client.post("/internal/v1/runs", json=request_body(), headers=headers(token="nope"))

    assert response.status_code == 401
    assert StubJava.received == []


def test_a_missing_capability_is_refused(tmp_path, java_stub) -> None:
    client = build_client(tmp_path, java_stub)

    response = client.post(
        "/internal/v1/runs", json=request_body(), headers=headers(capability=None)
    )

    assert response.status_code == 400
    assert StubJava.received == []


def test_a_payload_without_a_generation_is_refused(tmp_path, java_stub) -> None:
    client = build_client(tmp_path, java_stub)

    response = client.post(
        "/internal/v1/runs", json=request_body(execution_generation=None), headers=headers()
    )

    assert response.status_code == 422
    assert StubJava.received == []


def test_an_unknown_contract_field_is_refused(tmp_path, java_stub) -> None:
    client = build_client(tmp_path, java_stub)

    response = client.post("/internal/v1/runs", json=request_body(extra="x"), headers=headers())

    assert response.status_code == 422
