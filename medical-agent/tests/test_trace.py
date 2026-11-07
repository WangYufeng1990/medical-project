"""Trace parsing, per-attempt binding and best-effort export (Round 59.2)."""

from __future__ import annotations

from pathlib import Path

from medical_agent.trace import Tracer, parse_traceparent

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
