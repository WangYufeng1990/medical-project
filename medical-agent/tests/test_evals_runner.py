"""The evaluation runner must fail when the code under test is wrong.

These tests drive the runner's handlers with deliberately broken validators: one that accepts
everything and one that refuses everything. The citation family must catch both, which is what
the runner previously could not do (an exception was treated as a pass for every kind).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "evals"))
sys.path.insert(0, str(ROOT / "src"))

import run as runner  # noqa: E402

from medical_agent.kb.citations import CitationError  # noqa: E402
from medical_agent.kb.retrieval import Retriever  # noqa: E402
from medical_agent.kb.store import KnowledgeBase  # noqa: E402


@pytest.fixture()
def retriever() -> Retriever:
    knowledge = KnowledgeBase(ROOT / "kb" / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    return Retriever(knowledge.chunks())


def cases() -> dict[str, dict]:
    rows = [json.loads(line) for line in
            (ROOT / "evals" / "cases.jsonl").read_text().splitlines() if line.strip()]
    return {row["id"]: row for row in rows}


def test_a_validator_that_refuses_everything_fails_the_valid_case(
    retriever: Retriever, monkeypatch: pytest.MonkeyPatch
) -> None:
    def refuse(citations, hits):  # type: ignore[no-untyped-def]
        raise CitationError("refused")

    monkeypatch.setattr(runner, "verify_citations", refuse)
    problems = runner.check_citation(cases()["cit-valid"], retriever)

    assert problems, "a rejected valid citation must be a failure"


def test_a_validator_that_accepts_everything_fails_the_forged_cases(
    retriever: Retriever, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(runner, "verify_citations", lambda citations, hits: None)

    assert runner.check_citation(cases()["cit-forged-document"], retriever)
    assert runner.check_citation(cases()["cit-not-retrieved"], retriever)


def test_the_real_validator_passes_the_real_cases(retriever: Retriever) -> None:
    assert runner.check_citation(cases()["cit-valid"], retriever) == []
    assert runner.check_citation(cases()["cit-forged-hash"], retriever) == []


def test_the_report_carries_the_run_identity_and_denominators(tmp_path) -> None:
    """59.1: a result is unreadable without knowing what produced it."""
    import subprocess as sp

    report = tmp_path / "report.json"
    sp.run([sys.executable, "-m", "evals.run", "--report", str(report)], cwd=ROOT,
           env={**__import__("os").environ, "PYTHONPATH": str(ROOT / "src")},
           check=False, capture_output=True)

    assert report.exists(), "the report is written even when recorded gaps fail the gate"
    payload = json.loads(report.read_text())

    assert payload["manifest"]["cases"] == 300, "the default run is the dev split"
    assert payload["manifest"]["splits"] == ["dev"]
    assert len(payload["manifest"]["dataset_sha256"]) == 64
    assert payload["manifest"]["splits"] == ["dev"]
    assert payload["manifest"]["provider"] == "mock-or-canned"
    assert payload["manifest"]["models"], "the models actually used must be recorded"
    assert payload["manifest"]["prompt_version"].startswith("sha256:")
    assert len(payload["manifest"]["prompt_version"]) == len("sha256:") + 64
    assert payload["manifest"]["commit"]
    # Three attack cases are recorded dev gaps: they carry the desired behaviour and currently fail.
    # The real dev report reflects progress; it is not pinned to today's gap count.
    assert payload["totals"]["cases"] == 300
    assert payload["totals"]["failed"] == len(payload["examples"])
    assert payload["totals"]["skipped"] == 0
    assert payload["totals"]["errors"] == 0
    assert {"classification", "uncertainty", "retrieval", "citations", "validation", "graph",
            "tools", "attacks"} <= set(payload["per_family"])
    assert "risk:EMERGENCY" in payload["per_class"], "classification is broken down by class"
    assert "intent:BILLING" in payload["per_class"]
    assert all(entry["total"] > 0 for entry in payload["per_class"].values())
    assert all(entry["total"] > 0 for entry in payload["per_family"].values())


def test_a_mistyped_family_fails_instead_of_passing(tmp_path) -> None:
    import os
    import subprocess as sp

    report = tmp_path / "empty.json"
    completed = sp.run([sys.executable, "-m", "evals.run", "--family", "retrival",
                        "--report", str(report)], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode != 0, "an empty selection must not exit zero"
    assert "no cases" in completed.stderr
    assert not report.exists(), "no report is written for an empty selection"


def test_a_failing_dataset_writes_the_report_and_exits_one(tmp_path) -> None:
    # A controlled failing case proves the CLI's failure contract independently of today's gaps.
    import os
    import subprocess as sp

    dataset = tmp_path / "one.jsonl"
    dataset.write_text(json.dumps({
        "id": "controlled-failure", "family": "classification", "kind": "classify",
        "split": "dev", "message": "What are your opening hours?",
        "model_answer": json.dumps({"intents": ["OTHER"], "risk": "ROUTINE", "confidence": 0.7}),
        "expect": {"intents_include": [], "risk": "EMERGENCY", "requires_clinician": True,
                   "flags_include": []},
    }) + "\n")
    report = tmp_path / "report.json"

    completed = sp.run([sys.executable, "-m", "evals.run", "--cases", str(dataset),
                        "--report", str(report)], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 1, "a failing case must fail the run"
    payload = json.loads(report.read_text())
    assert payload["totals"] == {"cases": 1, "failed": 1, "skipped": 0, "errors": 0}
    assert payload["examples"], "the failing example is reported"


def test_the_tool_check_requires_a_denial_with_no_call(monkeypatch) -> None:
    # A substitute that calls the client and then refuses must fail the evaluation (59.1 review).
    from evals import run as runner

    from medical_agent.tools import registry
    from medical_agent.tools.registry import TOOLS

    monkeypatch.setitem(TOOLS, "calls_then_refuses", TOOLS["get_my_appointments"])
    monkeypatch.setattr(registry, "ToolExecutor", _CallsThenRefuses)

    problems = runner.check_tool({
        "tool": "calls_then_refuses", "arguments": {},
        "expect": {"denied": "unknown_tool"},
    }, None)  # type: ignore[arg-type]

    assert problems and "client called" in problems[0]


class _CallsThenRefuses:
    """A stand-in executor: it reaches the registry, calls the client, then reports a refusal."""

    def __init__(self, client: object, session: object) -> None:
        self.client = client
        self.session = session

    def execute(self, name: str, arguments: dict[str, object]) -> object:
        from medical_agent.tools.registry import ToolDenied

        self.client("/called-before-refusing", 1.0)  # type: ignore[operator]
        raise ToolDenied("unknown_tool", "unsupported tool")


def test_a_case_that_expects_a_denial_fails_on_a_normal_result(monkeypatch) -> None:
    # An implementation that returns instead of refusing must not pass a denial case (59.1 review).
    from evals import run as runner

    from medical_agent.tools import registry

    monkeypatch.setattr(registry, "ToolExecutor", _ReturnsNormally)

    problems = runner.check_tool({
        "tool": "get_my_appointments", "arguments": {"limit": 500},
        "expect": {"denied": "invalid_arguments"},
    }, None)  # type: ignore[arg-type]

    assert problems and "expected denial" in problems[0]


class _ReturnsNormally:
    def __init__(self, client: object, session: object) -> None:
        pass

    def execute(self, name: str, arguments: dict[str, object]) -> object:
        from medical_agent.tools.registry import ToolResult

        return ToolResult(name=name, payload=[])


def test_the_budget_stops_before_the_second_case_is_measured(tmp_path) -> None:
    # A hard cap of one request: the second case is skipped, not passed or failed (59.1).
    import os
    import subprocess as sp

    dataset = tmp_path / "two.jsonl"
    rows = []
    for index in (1, 2):
        rows.append(json.dumps({
            "id": f"budget-{index}", "family": "classification", "kind": "classify",
            "split": "dev", "message": f"Where do I send my bill payment {index}?",
            "model_answer": json.dumps({"intents": ["BILLING"], "risk": "ROUTINE",
                                        "confidence": 0.9}),
            "expect": {"intents_include": ["BILLING"], "risk": "ROUTINE",
                       "requires_clinician": False, "flags_include": []},
        }))
    dataset.write_text("\n".join(rows) + "\n")
    report = tmp_path / "budget.json"

    completed = sp.run([sys.executable, "-m", "evals.run", "--cases", str(dataset),
                        "--max-requests", "1", "--report", str(report)], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 3, "an incomplete run must not exit zero"
    assert "INCOMPLETE" in completed.stdout
    payload = json.loads(report.read_text())
    assert payload["totals"] == {"cases": 2, "failed": 0, "skipped": 1, "errors": 0}
    assert payload["budget"]["requests"] == 1
    assert payload["budget"]["skipped"] == 1
    assert "price_per_request_usd" in payload["budget"]


def test_a_case_without_a_model_call_does_not_consume_the_budget(tmp_path) -> None:
    # A tool case runs first and must not spend the single allowed request (59.1 review).
    import os
    import subprocess as sp

    dataset = tmp_path / "mixed.jsonl"
    dataset.write_text("\n".join([
        json.dumps({"id": "tool-1", "family": "tools", "kind": "tool", "split": "dev",
                    "tool": "get_my_appointments", "arguments": {}, "response": [],
                    "expect": {"needs_human": False, "fact_count": 0}}),
        json.dumps({"id": "model-1", "family": "classification", "kind": "classify",
                    "split": "dev", "message": "Where do I send my bill payment?",
                    "model_answer": json.dumps({"intents": ["BILLING"], "risk": "ROUTINE",
                                                "confidence": 0.9}),
                    "expect": {"intents_include": ["BILLING"], "risk": "ROUTINE",
                               "requires_clinician": False, "flags_include": []}}),
    ]) + "\n")
    report = tmp_path / "mixed-report.json"

    completed = sp.run([sys.executable, "-m", "evals.run", "--cases", str(dataset),
                        "--max-requests", "1", "--report", str(report)], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 0, completed.stdout + completed.stderr
    payload = json.loads(report.read_text())
    assert payload["totals"] == {"cases": 2, "failed": 0, "skipped": 0, "errors": 0}
    assert payload["budget"]["requests"] == 1, "only the classify case sent a request"


def test_a_dollar_cap_without_a_price_is_refused(tmp_path) -> None:
    import os
    import subprocess as sp

    completed = sp.run([sys.executable, "-m", "evals.run", "--max-usd", "1"], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 2
    assert "--price-per-request-usd" in completed.stderr


def test_a_non_finite_dollar_cap_is_refused(tmp_path) -> None:
    import os
    import subprocess as sp

    for price in ("nan", "inf"):
        completed = sp.run([sys.executable, "-m", "evals.run", "--max-usd", "0.01",
                            "--price-per-request-usd", price], cwd=ROOT,
                           env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                           capture_output=True, text=True)
        assert completed.returncode == 2, price
        assert "finite" in completed.stderr


def test_free_cases_still_run_after_the_model_budget_is_gone(tmp_path) -> None:
    # classify -> classify -> tool with one request: the second classify is skipped, the tool runs.
    import os
    import subprocess as sp

    dataset = tmp_path / "mixed.jsonl"
    classify = {
        "family": "classification", "kind": "classify", "split": "dev",
        "message": "Where do I send my bill payment?",
        "model_answer": json.dumps({"intents": ["BILLING"], "risk": "ROUTINE",
                                    "confidence": 0.9}),
        "expect": {"intents_include": ["BILLING"], "risk": "ROUTINE",
                   "requires_clinician": False, "flags_include": []},
    }
    dataset.write_text("\n".join([
        json.dumps({"id": "m1", **classify}),
        json.dumps({"id": "m2", **classify}),
        json.dumps({"id": "tool-1", "family": "tools", "kind": "tool", "split": "dev",
                    "tool": "get_my_appointments", "arguments": {}, "response": [],
                    "expect": {"needs_human": False, "fact_count": 0}}),
    ]) + "\n")
    report = tmp_path / "report.json"

    completed = sp.run([sys.executable, "-m", "evals.run", "--cases", str(dataset),
                        "--max-requests", "1", "--report", str(report)], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 3, completed.stdout
    payload = json.loads(report.read_text())
    assert payload["totals"] == {"cases": 3, "failed": 0, "skipped": 1, "errors": 0}
    assert payload["budget"]["requests"] == 1


def test_a_live_run_without_a_request_cap_is_refused() -> None:
    import os
    import subprocess as sp

    completed = sp.run([sys.executable, "-m", "evals.run", "--provider", "deepseek"], cwd=ROOT,
                       env={**os.environ, "PYTHONPATH": str(ROOT / "src")},
                       capture_output=True, text=True)

    assert completed.returncode == 2
    assert "--max-requests" in completed.stderr


def test_an_upstream_failure_is_an_error_not_a_pass(tmp_path, monkeypatch) -> None:
    # Through the real classification path: the product degrades, the evaluation records it.
    from evals import run as runner

    from medical_agent.providers.base import ProviderError

    dataset = tmp_path / "one.jsonl"
    dataset.write_text(json.dumps({
        "id": "live-1", "family": "classification", "kind": "classify", "split": "dev",
        "message": "I have chest pain", "model_answer": "{}",
        "expect": {"intents_include": [], "risk": "EMERGENCY", "requires_clinician": True,
                   "flags_include": ["emergency:chest_pain"]},
    }) + "\n")
    report = tmp_path / "report.json"

    class Failing:
        def invoke_now(self, request):  # type: ignore[no-untyped-def]
            raise ProviderError("upstream down")

    monkeypatch.setattr(runner, "LIVE_PROVIDER", Failing())
    monkeypatch.setattr(sys, "argv", ["run.py", "--cases", str(dataset),
                                      "--max-requests", "1", "--report", str(report)])

    assert runner.main() == 3
    payload = json.loads(report.read_text())
    assert payload["totals"]["errors"] == 1
    assert payload["totals"]["failed"] == 0
