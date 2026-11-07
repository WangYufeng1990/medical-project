"""Round 59.3: a refused result submission must stay retryable, not be silently lost."""

from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import ClassVar

import httpx
import pytest

from medical_agent.graph.state import RunState
from medical_agent.java_client import (
    CallCredentials,
    reset_credentials,
    submit_result,
    use_credentials,
)


class FlakyJava(BaseHTTPRequestHandler):
    statuses: ClassVar[list[int]] = []
    bodies: ClassVar[list[dict[str, object]]] = []
    receipt_accepted: ClassVar[bool] = False

    def do_GET(self) -> None:
        if self.receipt_accepted:
            body = json.dumps({"code": 200, "data": {"runId": 200, "generation": 1,
                                                     "state": "WAITING_REVIEW", "hasResult": True,
                                                     "accepted": True}}).encode()
            self.send_response(200)
        else:
            body = b'{"code":404,"message":"no receipt","data":null}'
            self.send_response(404)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        FlakyJava.bodies.append(json.loads(self.rfile.read(length)))
        status = FlakyJava.statuses.pop(0) if FlakyJava.statuses else 200
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()

    def log_message(self, *args: object) -> None:
        return


@pytest.fixture()
def java() -> str:
    FlakyJava.statuses = []
    FlakyJava.bodies = []
    FlakyJava.receipt_accepted = False
    server = ThreadingHTTPServer(("127.0.0.1", 0), FlakyJava)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


def state_for() -> RunState:
    return {  # type: ignore[return-value]
        "run_id": 200, "execution_generation": 1, "classification": "BILLING",
        "risk": "ROUTINE", "requires_clinician": False, "draft": "Draft [chunk:x].",
        "adopted_citations": [], "validation": {"ok": True, "kind": "policy_answer",
                                                "reasons": [], "rejected_reasons": []},
    }


def test_a_429_keeps_the_result_retryable(java: str) -> None:
    FlakyJava.statuses = [429, 200]
    credentials = CallCredentials(capability="cap", java_base_url=java, outbound_token="out")

    token = use_credentials(credentials)
    try:
        with pytest.raises(httpx.HTTPStatusError):
            submit_result(state_for())
    finally:
        reset_credentials(token)

    assert len(FlakyJava.bodies) == 1

    token = use_credentials(credentials)
    try:
        submit_result(state_for())
    finally:
        reset_credentials(token)

    assert len(FlakyJava.bodies) == 2, "the retry sends the result again"
    assert FlakyJava.bodies[1]["draft"] == "Draft [chunk:x]."


def test_an_accepted_receipt_stops_a_resend(java: str) -> None:
    FlakyJava.receipt_accepted = True
    credentials = CallCredentials(capability="cap", java_base_url=java, outbound_token="out")

    token = use_credentials(credentials)
    try:
        submit_result(state_for())
    finally:
        reset_credentials(token)

    assert FlakyJava.bodies == [], "an accepted receipt must not be overridden"
