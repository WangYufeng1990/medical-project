"""Trace parsing, per-attempt binding and best-effort export (Round 59.2)."""

from __future__ import annotations

from pathlib import Path

from medical_agent.trace import OtlpExporter, Tracer, parse_traceparent

VALID = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"


def test_a_missing_or_malformed_header_starts_a_fresh_trace(tmp_path: Path) -> None:
    tracer = Tracer(tmp_path / "trace.jsonl")
    first = tracer.context.trace_id
    for bad in (None, "", "00-xyz-00f067aa0ba902b7-01", "01-4bf92f3577b34da6a3ce929d0e0e4736-"
                                                    "00f067aa0ba902b7-01",
                "00-" + "0" * 32 + "-" + "0" * 16 + "-01", "garbage"):
        tracer.bind(bad)
        assert tracer.context.trace_id not in {"4bf92f3577b34da6a3ce929d0e0e4736"}
        assert len(tracer.context.trace_id) == 32
    tracer.bind(VALID)
    assert tracer.context.trace_id == "4bf92f3577b34da6a3ce929d0e0e4736"
    assert tracer.context.trace_id != first


def test_binding_again_does_not_keep_the_previous_trace(tmp_path: Path) -> None:
    tracer = Tracer(tmp_path / "trace.jsonl")
    tracer.bind(VALID)
    tracer.bind(None)
    assert tracer.context.trace_id != "4bf92f3577b34da6a3ce929d0e0e4736"


def test_spans_record_the_parent_span_id(tmp_path: Path) -> None:
    path = tmp_path / "trace.jsonl"
    tracer = Tracer(path)
    tracer.bind(VALID)
    parent = tracer.context.span_id
    with tracer.span("classify_message"):
        pass
    line = path.read_text().strip()
    assert f'"parent_span_id": "{parent}"' in line


def test_an_unwritable_export_does_not_fail_the_node(tmp_path: Path) -> None:
    tracer = Tracer(tmp_path / "missing-dir" / "trace.jsonl")
    with tracer.span("classify_message"):
        pass  # the node succeeded; the exporter could not write
    assert tracer.export_errors == 1


def test_parse_rejects_all_zero_and_short_ids() -> None:
    assert parse_traceparent(VALID) is not None
    assert parse_traceparent("00-" + "a" * 31 + "-" + "b" * 16 + "-01") is None
    assert parse_traceparent("00-" + "a" * 32 + "-" + "0" * 16 + "-01") is None


def test_two_concurrent_requests_keep_their_own_trace_and_parent(tmp_path: Path) -> None:
    # One shared tracer, two threads: each span must carry the trace and parent it started with.
    import json
    import threading

    path = tmp_path / "trace.jsonl"
    tracer = Tracer(path)
    a_bound = threading.Event()
    b_done = threading.Event()
    results: dict[str, tuple[str, str, str]] = {}

    def request_a() -> None:
        tracer.bind("00-" + "a" * 32 + "-" + "b" * 16 + "-01")
        parent = tracer.context.span_id
        with tracer.span("classify_message"):
            a_bound.set()
            assert b_done.wait(timeout=5), "the second request must finish while the first is open"
        # A second span for the same request after B rebound must still belong to A.
        with tracer.span("draft_reply"):
            pass
        results["a"] = ("a" * 32, parent, "classify_message")

    def request_b() -> None:
        assert a_bound.wait(timeout=5)
        tracer.bind("00-" + "c" * 32 + "-" + "d" * 16 + "-01")
        with tracer.span("retrieve_evidence"):
            pass
        b_done.set()

    thread_a = threading.Thread(target=request_a)
    thread_b = threading.Thread(target=request_b)
    thread_a.start()
    thread_b.start()
    thread_a.join(timeout=10)
    thread_b.join(timeout=10)

    spans = [json.loads(line) for line in path.read_text().splitlines()]
    by_name = {span["name"]: span for span in spans}
    assert by_name["classify_message"]["trace_id"] == "a" * 32
    assert by_name["classify_message"]["parent_span_id"] == results["a"][1]
    assert by_name["retrieve_evidence"]["trace_id"] == "c" * 32
    assert by_name["draft_reply"]["trace_id"] == "a" * 32, "A's later span must stay in A's trace"
    assert by_name["draft_reply"]["parent_span_id"] == results["a"][1]


def test_the_dispatch_header_reaches_the_worker_trace(tmp_path: Path) -> None:
    # The API binds the header per dispatch; the worker's graph then exports under that trace.
    import json

    from fastapi.testclient import TestClient

    from medical_agent.api.app import create_app
    from medical_agent.checkpoints import open_checkpointer
    from medical_agent.config import Settings
    from medical_agent.providers.mock import MockProvider
    from medical_agent.worker import AgentWorker

    trace_file = tmp_path / "trace.jsonl"
    settings = Settings(inbound_token="token", outbound_token="out", trace_file=str(trace_file))
    with open_checkpointer(f"sqlite://{tmp_path / 'api.sqlite'}") as checkpointer:
        worker = AgentWorker(MockProvider(), checkpointer, lambda state: None,
                             tracer=Tracer(trace_file))
        app = create_app(settings, worker=worker)
        with TestClient(app) as client:
            response = client.post(
                "/internal/v1/runs",
                json={"contract_version": "v1", "run_id": 150, "message_id": 1500,
                      "patient_id": 100, "conversation_revision": 1, "execution_generation": 1,
                      "content": "Can I move my appointment?", "request_id": "req-trace-150"},
                headers={"Authorization": "Bearer token", "X-Agent-Capability": "cap",
                         "traceparent": "00-" + "e" * 32 + "-" + "f" * 16 + "-01"},
            )

    assert response.status_code == 202, response.text
    spans = [json.loads(line) for line in trace_file.read_text().splitlines()]
    assert spans, "the dispatch is traced"
    assert {span["trace_id"] for span in spans} == {"e" * 32}
    assert {span["parent_span_id"] for span in spans} == {"f" * 16}


def test_the_otlp_exporter_posts_standard_span_fields(tmp_path: Path) -> None:
    # Langfuse accepts OTLP; the payload shape is what a collector would receive (59.2).
    import json
    import threading
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    received: list[dict[str, object]] = []

    class Collector(BaseHTTPRequestHandler):
        def do_POST(self) -> None:
            length = int(self.headers.get("Content-Length", "0"))
            received.append(json.loads(self.rfile.read(length)))
            self.send_response(200)
            self.end_headers()

        def log_message(self, *args: object) -> None:
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Collector)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        tracer = Tracer(tmp_path / "trace.jsonl",
                        otlp=OtlpExporter(f"http://127.0.0.1:{server.server_address[1]}/otel"))
        tracer.bind("00-" + "a" * 32 + "-" + "b" * 16 + "-01")
        with tracer.span("classify_message", run_id=1):
            pass
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()

    assert received, "a span was posted"
    span = received[0]["resourceSpans"][0]["scopeSpans"][0]["spans"][0]
    assert span["traceId"] == "a" * 32
    assert span["parentSpanId"] == "b" * 16
    assert span["name"] == "classify_message"
    assert span["attributes"] == [{"key": "run_id", "value": {"stringValue": "1"}}]


def test_a_dead_collector_does_not_fail_the_node(tmp_path: Path) -> None:
    tracer = Tracer(tmp_path / "trace.jsonl", otlp=OtlpExporter("http://127.0.0.1:1/otel"))
    with tracer.span("classify_message"):
        pass
    assert tracer.otlp is not None and tracer.otlp.failures == 1


def test_otlp_runs_even_without_a_jsonl_path() -> None:
    import json
    import threading
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    received: list[dict[str, object]] = []

    class Collector(BaseHTTPRequestHandler):
        def do_POST(self) -> None:
            received.append(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))
            self.send_response(200)
            self.end_headers()

        def log_message(self, *args: object) -> None:
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Collector)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        tracer = Tracer(None, otlp=OtlpExporter(
            f"http://127.0.0.1:{server.server_address[1]}/otel"))
        with tracer.span("classify_message"):
            pass
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()

    assert received, "OTLP must not depend on the JSONL sink"


def test_a_collector_that_rejects_the_payload_counts_as_a_failure() -> None:
    import threading
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    class Rejecting(BaseHTTPRequestHandler):
        def do_POST(self) -> None:
            self.send_response(500)
            self.end_headers()

        def log_message(self, *args: object) -> None:
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Rejecting)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    exporter = OtlpExporter(f"http://127.0.0.1:{server.server_address[1]}/otel")
    try:
        with Tracer(None, otlp=exporter).span("classify_message"):
            pass
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()

    assert exporter.failures == 1
