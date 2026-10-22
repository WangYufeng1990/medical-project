"""Round 55.4 — the run intake the Java dispatcher calls.

The Java side is a local HTTP server here, so the test sees the result submission exactly as
the business layer will: header names, credentials and body.
"""

from __future__ import annotations

import json
import sqlite3
import threading
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import ClassVar

import pytest
from fastapi.testclient import TestClient

from medical_agent.api.app import create_app
from medical_agent.config import Settings
from medical_agent.providers.base import ProviderRequest, ProviderResponse

INBOUND = "inbound-token-for-tests"
OUTBOUND = "outbound-token-for-tests"
CAPABILITY = "capability-for-tests"


class StubJava(BaseHTTPRequestHandler):
    received: ClassVar[list[dict[str, object]]] = []
    # When set, the stub behaves like Java's generation check: a result for any other
    # generation is refused with 409, exactly as AgentInternalController does.
    expected_generation: ClassVar[int | None] = None

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
        status = 200
        if (
            StubJava.expected_generation is not None
            and body.get("generation") != StubJava.expected_generation
        ):
            status = 409
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"code":200,"message":"ok"}' if status == 200
                         else b'{"code":409,"message":"stale generation"}')

    def log_message(self, format: str, *args: object) -> None:
        pass


class StubProvider:
    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        return ProviderResponse(text="mock", provider="mock", model="mock-1", latency_ms=1)


@pytest.fixture()
def java_stub() -> Iterator[str]:
    StubJava.received = []
    StubJava.expected_generation = None
    server = ThreadingHTTPServer(("127.0.0.1", 0), StubJava)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


@pytest.fixture()
def client(tmp_path: Path, java_stub: str) -> Iterator[TestClient]:
    # No worker is injected: the application builds it in its lifespan, which is what the
    # checkpointer lifetime fix is about.
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{tmp_path / 'api.sqlite'}",
    )
    app = create_app(settings, provider=StubProvider())
    with TestClient(app) as started:
        yield started


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
    client: TestClient,
) -> None:
    response = client.post("/internal/v1/runs", json=request_body(), headers=headers())

    assert response.status_code == 202
    assert response.json()["run_id"] == 41
    assert len(StubJava.received) == 1
    sent = StubJava.received[0]
    assert sent["path"] == "/api/v1/internal/agent/runs/41/results"
    assert sent["service_token"] == OUTBOUND
    assert sent["capability"] == CAPABILITY
    assert sent["body"] == {"generation": 1, "outcome": "APPOINTMENT:DRAFT_READY"}


def test_a_wrong_inbound_token_is_refused(client: TestClient) -> None:
    response = client.post("/internal/v1/runs", json=request_body(), headers=headers(token="nope"))

    assert response.status_code == 401
    assert StubJava.received == []


def test_a_missing_capability_is_refused(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/runs", json=request_body(), headers=headers(capability=None)
    )

    assert response.status_code == 400
    assert StubJava.received == []


def test_a_payload_without_a_generation_is_refused(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/runs", json=request_body(execution_generation=None), headers=headers()
    )

    assert response.status_code == 422
    assert StubJava.received == []


def test_an_unknown_contract_field_is_refused(client: TestClient) -> None:
    response = client.post("/internal/v1/runs", json=request_body(extra="x"), headers=headers())

    assert response.status_code == 422


def test_the_checkpointer_is_closed_when_the_application_stops(
    tmp_path: Path, java_stub: str
) -> None:
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{tmp_path / 'lifetime.sqlite'}",
    )
    app = create_app(settings, provider=StubProvider())

    with TestClient(app) as started:
        checkpointer = app.state.checkpointer
        assert started.get("/health/live").status_code == 200
        assert checkpointer.conn.execute("SELECT 1").fetchall() != []

    with pytest.raises(sqlite3.ProgrammingError):
        checkpointer.conn.execute("SELECT 1")


def test_a_java_retry_with_a_new_generation_takes_over_the_finished_work(
    tmp_path: Path, java_stub: str
) -> None:
    # The real retry order: generation 1 is refused by Java (409), the dispatcher re-claims
    # as generation 2 and sends the same run again. The worker must adopt generation 2 and
    # submit it, without classifying or drafting a second time.
    StubJava.expected_generation = 2
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{tmp_path / 'retry.sqlite'}",
    )
    app = create_app(settings, provider=StubProvider())

    # A refused submission is a server-side failure; the client must see the 500 the Java
    # dispatcher would see, not a re-raised exception.
    with TestClient(app, raise_server_exceptions=False) as started:
        first = started.post("/internal/v1/runs", json=request_body(), headers=headers())
        assert first.status_code == 500, "a refused submission surfaces as a failed dispatch"
        second = started.post(
            "/internal/v1/runs",
            json=request_body(execution_generation=2),
            headers=headers(capability="capability-for-retry"),
        )
        assert second.status_code == 202

    generations = [sent["body"]["generation"] for sent in StubJava.received]
    assert generations == [1, 2], f"one attempt per generation: {generations}"
    assert StubJava.received[-1]["capability"] == "capability-for-retry"
