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
from dataclasses import dataclass
from pathlib import Path

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


class Tracer:
    """Writes one JSON line per finished span."""

    def __init__(self, path: Path | str | None, context: TraceContext | None = None) -> None:
        self.path = Path(path) if path else None
        self.context = context or new_context()
        self.export_errors = 0

    def bind(self, value: str | None) -> None:
        """Binds a fresh context per attempt; a missing or invalid header starts a new trace."""
        self.context = parse_traceparent(value) or new_context()

    @contextmanager
    def span(self, name: str, **attributes: object) -> Iterator[None]:
        started = time.time()
        parent_id = self.context.span_id
        span_id = secrets.token_hex(8)
        status = "ok"
        try:
            yield
        except BaseException:
            status = "error"
            raise
        finally:
            if self.path is not None:
                record = {"trace_id": self.context.trace_id, "span_id": span_id,
                          "parent_span_id": parent_id,
                          "name": name, "start": started, "end": time.time(),
                          "duration_ms": round((time.time() - started) * 1000, 2),
                          "status": status,
                          "attributes": {key: value for key, value in attributes.items()
                                         if value is not None}}
                try:
                    with self.path.open("a", encoding="utf-8") as handle:
                        handle.write(json.dumps(record, sort_keys=True) + "\n")
                except OSError:
                    # Tracing is best effort: a broken exporter must not change the task's result.
                    self.export_errors += 1


def tracer_from_env() -> Tracer:
    return Tracer(os.environ.get("MEDICAL_AGENT_TRACE_FILE"))
