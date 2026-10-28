#!/usr/bin/env python3
"""Runs the Round 56 evaluation set and prints a per-family report.

The runner is deterministic: every case either uses the built-in mock provider or the canned
answer written into the case, so a failure is about our code or our expectation, never about a
model's mood. It is kept separate from the unit suite on purpose - a fixture that changes
shape is a reviewable diff in cases.jsonl, not a silent test edit.

Usage:  PYTHONPATH=src .venv/bin/python evals/run.py [--family NAME]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from medical_agent.checkpoints import open_checkpointer  # noqa: E402
from medical_agent.classification.classifier import Classifier  # noqa: E402
from medical_agent.graph.nodes import DraftBlocked  # noqa: E402
from medical_agent.java_client import CallCredentials  # noqa: E402
from medical_agent.kb.chunking import Chunk  # noqa: E402
from medical_agent.kb.citations import (  # noqa: E402
    Citation,
    CitationError,
    build_citations,
    verify_citations,
)
from medical_agent.kb.retrieval import Retriever  # noqa: E402
from medical_agent.kb.store import KnowledgeBase  # noqa: E402
from medical_agent.providers.base import ProviderError, ProviderResponse  # noqa: E402
from medical_agent.schemas.classification import RiskLevel  # noqa: E402
from medical_agent.schemas.run import RunStartRequest  # noqa: E402
from medical_agent.validation import validate_draft  # noqa: E402
from medical_agent.worker import AgentWorker  # noqa: E402

CASES = Path(__file__).with_name("cases.jsonl")
CORPUS = ROOT / "kb"
RISK_ORDER = {RiskLevel.ROUTINE: 0, RiskLevel.URGENT: 1, RiskLevel.EMERGENCY: 2}


class CannedProvider:
    def __init__(self, text: str | None, error: str | None = None) -> None:
        self._text = text
        self._error = error

    def invoke_now(self, request) -> ProviderResponse:  # type: ignore[no-untyped-def]
        if self._error:
            raise ProviderError(self._error)
        return ProviderResponse(text=self._text or "", provider="canned", model="canned-1",
                                latency_ms=0)


class MockProvider:
    name = "mock"

    def invoke_now(self, request) -> ProviderResponse:  # type: ignore[no-untyped-def]
        return ProviderResponse(
            text='{"intents": ["OTHER"], "risk": "ROUTINE", "confidence": 0.5}',
            provider="mock", model="mock-1", latency_ms=0)


def load_cases(family: str | None) -> list[dict]:
    cases = [json.loads(line) for line in CASES.read_text().splitlines() if line.strip()]
    return [case for case in cases if family is None or case["family"] == family]


def corpus_retriever() -> Retriever:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    return Retriever(knowledge.chunks)


def contains(actual: list[str], expected: list[str]) -> bool:
    return all(item in actual for item in expected)


def check_classify(case: dict, retriever: Retriever) -> list[str]:
    expect = case["expect"]
    provider = MockProvider() if case["model_answer"] is None else CannedProvider(
        case["model_answer"])
    decision = Classifier(provider).classify(case["message"])
    problems: list[str] = []
    if not contains([intent.value for intent in decision.intents], expect["intents_include"]):
        problems.append(f"intents {[i.value for i in decision.intents]}")
    if decision.risk.value != expect["risk"]:
        problems.append(f"risk {decision.risk.value}")
    if decision.requires_clinician != expect["requires_clinician"]:
        problems.append(f"clinician {decision.requires_clinician}")
    if not contains(decision.flags, expect["flags_include"]):
        problems.append(f"flags {decision.flags}")
    return problems


def check_uncertain(case: dict, retriever: Retriever) -> list[str]:
    expect = case["expect"]
    provider = CannedProvider(case["model_answer"], case.get("provider_error"))
    decision = Classifier(provider).classify(case["message"])
    problems: list[str] = []
    if RISK_ORDER[decision.risk] < RISK_ORDER[RiskLevel(expect["risk_at_least"])]:
        problems.append(f"risk {decision.risk.value}")
    if not contains(decision.flags, expect["flags_include"]):
        problems.append(f"flags {decision.flags}")
    if decision.requires_clinician != expect["requires_clinician"]:
        problems.append(f"clinician {decision.requires_clinician}")
    if decision.confidence != expect["confidence"]:
        problems.append(f"confidence {decision.confidence}")
    return problems


def check_retrieve(case: dict, retriever: Retriever) -> list[str]:
    expect = case["expect"]
    result = retriever.search(case["query"], limit=case.get("limit", 4))
    problems: list[str] = []
    if expect.get("empty") is not None and result.empty != expect["empty"]:
        problems.append(f"empty {result.empty}")
    if expect.get("top_document") and (result.empty or
                                       result.hits[0].chunk.document_id != expect["top_document"]):
        problems.append("top document")
    if expect.get("max_hits") and len(result.hits) > expect["max_hits"]:
        problems.append(f"hits {len(result.hits)}")
    if expect.get("conflict") is not None and result.conflict != expect["conflict"]:
        problems.append(f"conflict {result.conflict}")
    return problems


def check_retrieve_conflict(case: dict, retriever: Retriever) -> list[str]:
    text = "Billing policy — Payment\nA payment plan changes the schedule."
    digest = hashlib.sha256(text.encode()).hexdigest()

    def chunk(chunk_id: str, document_id: str) -> Chunk:
        return Chunk(id=chunk_id, document_id=document_id, document_version=1,
                     title="Billing policy", heading_path=("Payment",), text=text,
                     content_hash=digest)

    result = Retriever((chunk("billing-a:1:000", "billing-a"),
                        chunk("billing-a:1:001", "billing-a"),
                        chunk("billing-b:1:000", "billing-b"))).search("payment plan")
    return [] if result.conflict == case["expect"]["conflict"] else ["conflict"]


def check_citation(case: dict, retriever: Retriever) -> list[str]:
    result = retriever.search(case["query"])
    citations = build_citations(result.hits)
    if not citations:
        return ["no citations"]
    try:
        if case["kind"] == "citation_valid":
            verify_citations(citations, result.hits)
            return []
        if case["kind"] == "citation_not_retrieved":
            forged = Citation(chunk_id=case["chunk_id"], document_id="appointments",
                              document_version=1, title="Forged", heading_path=("x",),
                              content_hash="0" * 64)
            verify_citations((*citations, forged), result.hits)
        else:
            first = citations[0]
            values = {
                "chunk_id": first.chunk_id,
                "document_id": first.document_id,
                "document_version": first.document_version,
                "title": first.title,
                "heading_path": first.heading_path,
                "content_hash": first.content_hash,
            }
            values[case["field"]] = case["value"]
            verify_citations((Citation(**values),), result.hits)  # type: ignore[arg-type]
        return ["accepted a forged citation"]
    except CitationError:
        return []


def check_validate(case: dict, retriever: Retriever) -> list[str]:
    state = {"evidence": case["evidence"], "citations": case["citations"],
             "requires_clinician": case["require_citation"] is False}
    validation = validate_draft(case["draft"], state, require_citation=case["require_citation"])
    problems: list[str] = []
    if validation.ok != case["expect"]["ok"]:
        problems.append(f"ok {validation.ok}: {validation.reasons}")
    for reason in case["expect"]["reasons_include"]:
        if not any(actual.startswith(reason) for actual in validation.reasons):
            problems.append(f"missing reason {reason}")
    return problems


def check_graph(case: dict, retriever: Retriever) -> list[str]:
    provider = CannedProvider(case["model_answer"])
    with tempfile.TemporaryDirectory() as directory, open_checkpointer(
        f"sqlite://{Path(directory) / 'eval.sqlite'}"
    ) as checkpointer:
        worker = AgentWorker(provider, checkpointer, lambda state: None,
                             retriever=retriever if case["retriever"] else None)
        try:
            state = worker.process(
                RunStartRequest(run_id=1, message_id=10, patient_id=100,
                                conversation_revision=1, execution_generation=1,
                                content=case["message"], request_id="req-eval-0001"),
                CallCredentials(capability="c", java_base_url="http://127.0.0.1:1",
                                outbound_token="o"),
            )
        except DraftBlocked as blocked:
            return [f"blocked: {blocked}"]
    expect = case["expect"]
    problems: list[str] = []
    kind = (state.get("validation") or {}).get("kind")
    if kind != expect["kind"]:
        problems.append(f"kind {kind}")
    adopted = sorted({str(item["document_id"]) for item in state.get("adopted_citations") or []})
    if adopted != expect["adopted_documents"]:
        problems.append(f"adopted {adopted}")
    if state.get("review_required") is not expect["review_required"]:
        problems.append(f"review_required {state.get('review_required')}")
    if expect.get("requires_clinician") is not None and (
        state.get("requires_clinician") is not expect["requires_clinician"]
    ):
        problems.append(f"clinician {state.get('requires_clinician')}")
    if (state.get("validation") or {}).get("ok") is not expect["validation_ok"]:
        problems.append(f"validation_ok {(state.get('validation') or {}).get('ok')}")
    if not contains(list(state.get("flags") or []), expect.get("flags_include") or []):
        problems.append(f"flags {state.get('flags')}")
    return problems


HANDLERS = {
    "classify": check_classify,
    "uncertain": check_uncertain,
    "retrieve": check_retrieve,
    "retrieve_conflict": check_retrieve_conflict,
    "citation_valid": check_citation,
    "citation_forged": check_citation,
    "citation_not_retrieved": check_citation,
    "validate": check_validate,
    "graph": check_graph,
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--family")
    args = parser.parse_args()

    retriever = corpus_retriever()
    cases = load_cases(args.family)
    per_family: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    failures: list[str] = []
    for case in cases:
        problems = HANDLERS[case["kind"]](case, retriever)
        per_family[case["family"]][0] += 1
        if problems:
            per_family[case["family"]][1] += 1
            failures.append(f"{case['id']} ({case['family']}): {'; '.join(problems)}")

    print(f"cases: {len(cases)}")
    for family in sorted(per_family):
        total, failed = per_family[family]
        print(f"  {family:<14} {total - failed:>3}/{total:<3} passed")
    if failures:
        print("\nfailures:")
        for failure in failures:
            print("  " + failure)
        return 1
    print("\nall cases passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
