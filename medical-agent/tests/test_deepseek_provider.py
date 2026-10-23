"""Round 56.1 — the DeepSeek adapter's contract and timeouts.

The adapter is the only place that talks to a real model, so its failure modes are measured
against a local server: what it sends, what it accepts, and what a slow or broken answer
becomes. No credential is involved; the key here is a placeholder.
"""

from __future__ import annotations

import json
import threading
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import ClassVar

import pytest

from medical_agent.config import Settings
from medical_agent.providers.base import ProviderError, ProviderRequest
from medical_agent.providers.deepseek import DeepSeekProvider

SENSITIVE = "patient-text-8c1d"


class StubModel(BaseHTTPRequestHandler):
    requests: ClassVar[list[dict[str, object]]] = []
    status: ClassVar[int] = 200
    body: ClassVar[str] = ""
    delay_seconds: ClassVar[float] = 0.0

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length))
        StubModel.requests.append(
            {"path": self.path, "authorization": self.headers.get("Authorization"),
             "body": payload}
        )
        if StubModel.delay_seconds:
            import time

            time.sleep(StubModel.delay_seconds)
        encoded = StubModel.body.encode()
        self.send_response(StubModel.status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format: str, *args: object) -> None:
        pass


def completion(text: str) -> str:
    return json.dumps({"choices": [{"message": {"content": text}}]})


@pytest.fixture()
def model_server() -> Iterator[str]:
    StubModel.requests = []
    StubModel.status = 200
    StubModel.body = completion('{"intents": ["OTHER"], "risk": "ROUTINE", "confidence": 0.9}')
    StubModel.delay_seconds = 0.0
    server = ThreadingHTTPServer(("127.0.0.1", 0), StubModel)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


def provider_for(base_url: str, timeout: float = 5.0) -> DeepSeekProvider:
    return DeepSeekProvider(
        Settings(
            inbound_token="inbound",
            outbound_token="outbound",
            provider="deepseek",
            deepseek_api_key="sk-placeholder",
            deepseek_base_url=base_url,
            deepseek_model="deepseek-chat",
            request_timeout_seconds=timeout,
        )
    )


def test_the_request_carries_the_model_the_bound_and_no_sampling(model_server: str) -> None:
    provider = provider_for(model_server)

    response = provider.invoke_now(ProviderRequest(prompt="classify this", max_output_tokens=32))

    sent = StubModel.requests[0]
    assert sent["path"] == "/chat/completions"
    assert sent["authorization"] == "Bearer sk-placeholder"
    body = sent["body"]
    assert body["model"] == "deepseek-chat"
    assert body["max_tokens"] == 32
    assert body["temperature"] == 0
    assert body["messages"] == [{"role": "user", "content": "classify this"}]
    assert response.text.startswith('{"intents"')
    assert response.provider == "deepseek"
    assert response.latency_ms >= 0


def test_a_non_200_answer_is_a_failure_without_the_body(model_server: str) -> None:
    StubModel.status = 422
    StubModel.body = json.dumps({"detail": f"invalid input: {SENSITIVE}"})

    with pytest.raises(ProviderError) as caught:
        provider_for(model_server).invoke_now(ProviderRequest(prompt="classify this"))

    assert "422" in str(caught.value)
    assert SENSITIVE not in str(caught.value)


def test_a_body_of_the_wrong_shape_is_a_failure(model_server: str) -> None:
    StubModel.body = json.dumps({"unexpected": True})

    with pytest.raises(ProviderError, match="unexpected shape"):
        provider_for(model_server).invoke_now(ProviderRequest(prompt="x"))


def test_an_empty_answer_is_a_failure(model_server: str) -> None:
    StubModel.body = completion("   ")

    with pytest.raises(ProviderError, match="no text"):
        provider_for(model_server).invoke_now(ProviderRequest(prompt="x"))


def test_a_slow_model_times_out(model_server: str) -> None:
    StubModel.delay_seconds = 1.0

    with pytest.raises(ProviderError, match="timed out"):
        provider_for(model_server, timeout=0.2).invoke_now(ProviderRequest(prompt="x"))


def test_an_unreachable_model_is_a_failure() -> None:
    provider = provider_for("http://127.0.0.1:1", timeout=0.5)

    with pytest.raises(ProviderError, match="could not be reached"):
        provider.invoke_now(ProviderRequest(prompt="x"))
