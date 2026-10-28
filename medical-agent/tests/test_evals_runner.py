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
