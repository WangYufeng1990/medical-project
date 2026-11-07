"""The live-probe verifier fails when a node span is missing (Round 59.2)."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "evals" / "verify_trace_link.py"
NODES = ("validate_content", "classify_message", "retrieve_evidence", "read_appointment",
         "draft_reply")


def run(tmp_path: Path, nodes: tuple[str, ...], canary_in_export: bool = False) -> int:
    java = tmp_path / "java.json"
    java.write_text(json.dumps({"name": "agent.dispatch", "traceId": "a" * 32,
                                "spanId": "b" * 16}) + "\n")
    python_export = tmp_path / "python.jsonl"
    lines = [json.dumps({"trace_id": "a" * 32, "parent_span_id": "b" * 16, "name": name})
             for name in nodes]
    if canary_in_export:
        lines.append(json.dumps({"trace_id": "a" * 32, "parent_span_id": "b" * 16,
                                 "name": "note", "attributes": {"text": "canary-9f"}}))
    python_export.write_text("\n".join(lines) + "\n")
    collector = tmp_path / "collector.bin"
    collector.write_bytes(b"payload")

    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--java", str(java), "--python", str(python_export),
         "--collector", str(collector), "--canary", "canary-9f"],
        capture_output=True, text=True)
    return completed.returncode


def test_all_five_nodes_pass(tmp_path: Path) -> None:
    assert run(tmp_path, NODES) == 0


def test_a_missing_node_fails(tmp_path: Path) -> None:
    assert run(tmp_path, NODES[:-1]) == 1


def test_a_canary_in_the_export_fails(tmp_path: Path) -> None:
    assert run(tmp_path, NODES, canary_in_export=True) == 1
