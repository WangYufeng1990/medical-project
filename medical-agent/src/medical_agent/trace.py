"""Minimal W3C-traceparent tracing with a JSONL exporter (Round 59.2).

Spans carry only identifiers, node names, status and timing — never message text, evidence or tool
payloads — so an exported trace cannot become a second copy of the PHI it describes.
"""

from __future__ import annotations

import json
import os
import re
import secrets
import time
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from pathlib import Path
from typing import Any

TRACEPARENT = "traceparent"


@dataclass(frozen=True)
class TraceContext:
    trace_id: str
    span_id: str

    def child(self) -> TraceContext:
        return TraceContext(self.trace_id, secrets.token_hex(8))


def new_context() -> TraceContext:
    return TraceContext(secrets.token_hex(16), secrets.token_hex(8))


_TRACEPARENT = re.compile(r"^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")
_ZERO = re.compile(r"^0+$")


def parse_traceparent(value: str | None) -> TraceContext | None:
    """Accepts exactly `00-<32 hex>-<16 hex>-<2 hex flags>`; anything else starts a fresh trace."""
    if not value:
        return None
    match = _TRACEPARENT.match(value.strip().lower())
    if match is None:
        return None
    trace_id, span_id, _flags = match.groups()
    if _ZERO.match(trace_id) or _ZERO.match(span_id):
        return None
    return TraceContext(trace_id, span_id)


def format_traceparent(context: TraceContext) -> str:
    return f"00-{context.trace_id}-{context.span_id}-01"


class OtlpExporter:
    """Posts OTLP/HTTP JSON spans; any Langfuse OTLP endpoint accepts this shape (59.2).

    Like the JSONL writer this is best effort: a collector that is down must not affect a run.
    """

    def __init__(self, endpoint: str, headers: dict[str, str] | None = None,
                 timeout: float = 2.0) -> None:
        self.endpoint = endpoint
        self.headers = headers or {}
        self.timeout = timeout
        self.failures = 0

    def export(self, records: list[dict[str, object]]) -> None:
        if not records:
            return
        body = {"resourceSpans": [{"resource": {"attributes": [
                    {"key": "service.name", "value": {"stringValue": "medical-agent"}}]},
                    "scopeSpans": [{"scope": {"name": "medical-agent.trace"},
                                    "spans": [self._span(record) for record in records]}]}]}
        try:
            import httpx

            response = httpx.post(self.endpoint, json=body, headers=self.headers,
                                  timeout=self.timeout)
            if response.status_code >= 300:
                self.failures += 1
        except Exception:
            self.failures += 1

    @staticmethod
    def _span(record: dict[str, Any]) -> dict[str, Any]:
        attributes = record.get("attributes") or {}
        return {
            "traceId": record["trace_id"],
            "spanId": record["span_id"],
            "parentSpanId": record.get("parent_span_id", ""),
            "name": record["name"],
            "kind": 1,
            "startTimeUnixNano": int(float(record["start"]) * 1_000_000_000),
            "endTimeUnixNano": int(float(record["end"]) * 1_000_000_000),
            "attributes": [{"key": str(key), "value": {"stringValue": str(value)}}
                           for key, value in dict(attributes).items()],
            "status": {"code": 2 if record.get("status") == "error" else 1},
        }


class Tracer:
    """Writes one JSON line per finished span, with a context per request, not per tracer."""

    def __init__(self, path: Path | str | None, context: TraceContext | None = None,
                 otlp: OtlpExporter | None = None) -> None:
        self.path = Path(path) if path else None
        self.otlp = otlp
        self._context: ContextVar[TraceContext | None] = ContextVar("trace_context", default=None)
        if context is not None:
            self._context.set(context)
        self.export_errors = 0

    @property
    def context(self) -> TraceContext:
        current = self._context.get()
        if current is None:
            current = new_context()
            self._context.set(current)
        return current

    def bind(self, value: str | None) -> None:
        """Binds a fresh context per attempt; a missing or invalid header starts a new trace."""
        self._context.set(parse_traceparent(value) or new_context())

    @contextmanager
    def span(self, name: str, **attributes: object) -> Iterator[None]:
        started = time.time()
        # Captured once, so a concurrent request rebinding the tracer cannot change this span.
        context = self.context
        parent_id = context.span_id
        span_id = secrets.token_hex(8)
        status = "ok"
        try:
            yield
        except BaseException:
            status = "error"
            raise
        finally:
            if self.path is not None or self.otlp is not None:
                record = {"trace_id": context.trace_id, "span_id": span_id,
                          "parent_span_id": parent_id,
                          "name": name, "start": started, "end": time.time(),
                          "duration_ms": round((time.time() - started) * 1000, 2),
                          "status": status,
                          "attributes": {key: value for key, value in attributes.items()
                                         if value is not None}}
                if self.path is not None:
                    try:
                        with self.path.open("a", encoding="utf-8") as handle:
                            handle.write(json.dumps(record, sort_keys=True) + "\n")
                    except OSError:
                        # Best effort: a broken exporter must not change the task's result.
                        self.export_errors += 1
                # The two sinks are independent: a broken JSONL path must not stop the collector.
                if self.otlp is not None:
                    self.otlp.export([record])


def tracer_from_env() -> Tracer:
    endpoint = os.environ.get("MEDICAL_AGENT_OTLP_ENDPOINT")
    otlp = OtlpExporter(endpoint) if endpoint else None
    return Tracer(os.environ.get("MEDICAL_AGENT_TRACE_FILE"), otlp=otlp)
