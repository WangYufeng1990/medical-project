"""Round 55.4 — the run intake the Java dispatcher calls.

The Java side is a local HTTP server here, so the test sees the result submission exactly as
the business layer will: header names, credentials and body.
"""

from __future__ import annotations

import json
import sqlite3
import threading
import urllib.parse
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import ClassVar

import pytest
from fastapi.testclient import TestClient

from medical_agent.api.app import create_app
from medical_agent.checkpoints import open_checkpointer
from medical_agent.classification.classifier import Classifier
from medical_agent.config import Settings
from medical_agent.graph.builder import build_graph
from medical_agent.providers.base import ProviderRequest, ProviderResponse

INBOUND = "inbound-token-for-tests"
OUTBOUND = "outbound-token-for-tests"
CAPABILITY = "capability-for-tests"


class StubJava(BaseHTTPRequestHandler):
    received: ClassVar[list[dict[str, object]]] = []
    # When set, the stub behaves like Java's generation check: a result for any other
    # generation is refused with 409, exactly as AgentInternalController does.
    expected_generation: ClassVar[int | None] = None
    # When set, the stub answers the receipt query with an accepted result.
    accepted_receipt: ClassVar[bool] = False
    # The generation a receipt is accepted for; None means "accept regardless of what was asked".
    accepted_generation: ClassVar[int | None] = 1
    # Java accepts the result and then the response is lost: the worker keeps its pending submit.
    fail_result_response: ClassVar[bool] = False
    receipt_queries: ClassVar[list[str | None]] = []

    def do_GET(self) -> None:
        if "/receipt" not in self.path:
            self.send_response(404)
            self.end_headers()
            return
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        requested = query.get("generation", [None])[0]
        StubJava.receipt_queries.append(requested)
        # Java's rule: the answer is for one generation, and a missing or different one is not
        # accepted. A stub that always says yes hides a caller that forgets to send it.
        accepted = (StubJava.accepted_receipt and requested == "1"
                    and (StubJava.accepted_generation is None
                         or requested == str(StubJava.accepted_generation)))
        payload = {
            "code": 200,
            "message": "ok",
            "data": {
                "runId": 41,
                "generation": int(requested) if requested is not None else None,
                "state": "PUBLISHED" if accepted else "RUNNING",
                "hasResult": accepted,
                "resultGeneration": 1 if accepted else None,
                "accepted": accepted,
            },
        }
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

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
        if StubJava.fail_result_response:
            # Java stored the result; the response is what the worker never receives.
            status = 500
        elif (
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
        return ProviderResponse(text='{"intents": ["APPOINTMENT"], "risk": "ROUTINE", '
                                     '"confidence": 0.9}', provider="mock", model="mock-1",
                                latency_ms=1)

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        import asyncio

        return asyncio.run(self.invoke(request))


@pytest.fixture()
def java_stub() -> Iterator[str]:
    StubJava.received = []
    StubJava.expected_generation = None
    StubJava.accepted_generation = 1
    StubJava.accepted_receipt = False
    StubJava.fail_result_response = False
    StubJava.receipt_queries = []
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
    body = sent["body"]
    assert body["run_id"] == 41
    assert body["generation"] == 1
    assert body["intent"] == "APPOINTMENT"
    assert body["risk"] == "ROUTINE"
    assert body["requires_clinician"] is False
    assert body["draft"].startswith("According to the practice policy:")
    assert body["validation"]["ok"] is True
    assert len(body["citations"]) == 1
    assert body["citations"][0]["document_id"] == "appointments"


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


def test_a_recovery_that_finds_the_result_accepted_does_not_resubmit(
    tmp_path: Path, java_stub: str
) -> None:
    # The capability is revoked once a result is stored, so a resend would be refused with 403;
    # the receipt is what tells the worker the work is already done.
    StubJava.accepted_receipt = True
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{tmp_path / 'receipt.sqlite'}",
    )
    app = create_app(settings, provider=StubProvider())

    with TestClient(app) as started:
        response = started.post("/internal/v1/runs", json=request_body(), headers=headers())

    assert response.status_code == 202
    assert StubJava.received == [], "an accepted result must not be submitted again"


def test_a_receipt_for_another_generation_does_not_count(
    tmp_path: Path, java_stub: str
) -> None:
    # Java answers per generation: an accepted receipt for generation 1 must not stop the
    # submission of generation 2 (the run was retried).
    StubJava.accepted_receipt = True
    StubJava.accepted_generation = 1
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{tmp_path / 'other-generation.sqlite'}",
    )
    app = create_app(settings, provider=StubProvider())

    with TestClient(app) as started:
        response = started.post(
            "/internal/v1/runs",
            json=request_body(execution_generation=2),
            headers=headers(),
        )

    assert response.status_code == 202
    assert [entry["path"] for entry in StubJava.received] == [
        "/api/v1/internal/agent/runs/41/results"
    ], "a receipt for another generation must not suppress this submission"


def test_recovery_after_a_lost_result_response_asks_the_receipt_and_does_not_resend(
    tmp_path: Path, java_stub: str
) -> None:
    # Java stores the result but the response never reaches the worker, so the checkpoint keeps a
    # pending submit and no submitted_generation. A restart replays the same run/generation: the
    # worker must learn from the receipt that the result is accepted and post nothing.
    checkpoints = tmp_path / "lost-response.sqlite"
    StubJava.fail_result_response = True
    settings = Settings(
        inbound_token=INBOUND,
        outbound_token=OUTBOUND,
        java_base_url=java_stub,
        checkpoint_url=f"sqlite://{checkpoints}",
    )
    first_app = create_app(settings, provider=StubProvider())
    with TestClient(first_app, raise_server_exceptions=False) as started:
        first = started.post("/internal/v1/runs", json=request_body(), headers=headers())
    assert first.status_code == 500, "the lost response must surface as a failed dispatch"
    assert len(StubJava.received) == 1, "Java did receive the first submission"

    # The precondition is an assertion too: the checkpoint must still owe a submission and must not
    # have recorded the generation, otherwise the replay below would be answered before any receipt.
    with open_checkpointer(f"sqlite://{checkpoints}") as checkpointer:
        graph = build_graph(Classifier(StubProvider()), None, lambda state: None, checkpointer)
        snapshot = graph.get_state({"configurable": {"thread_id": "run-41"}})
        assert "submit_result" in snapshot.next, (
            f"the lost response must leave the submission pending, next was {snapshot.next}"
        )
        assert not snapshot.values.get("submitted_generation"), (
            "the checkpoint must not have recorded the submission"
        )

    # The restart: same checkpoint, Java now answers the receipt as accepted.
    StubJava.fail_result_response = False
    StubJava.accepted_receipt = True
    StubJava.received = []
    StubJava.receipt_queries = []
    second_app = create_app(settings, provider=StubProvider())
    with TestClient(second_app) as restarted:
        replay = restarted.post("/internal/v1/runs", json=request_body(), headers=headers())

    assert replay.status_code == 202
    assert StubJava.receipt_queries == ["1"], "recovery must ask Java about this generation"
    assert [entry["path"] for entry in StubJava.received] == [], (
        "an accepted result must not be posted again after a restart"
    )
