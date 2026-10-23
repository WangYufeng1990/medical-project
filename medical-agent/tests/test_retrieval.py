"""Round 56.3 — retrieval baselines and the hybrid.

The corpus is small on purpose, so these tests are about behaviour that must hold regardless of
size: a policy question finds its policy, a question with no shared vocabulary finds nothing (and
says so), the two baselines are both reported, and a tie between two documents is not resolved
by silently picking one.
"""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from medical_agent.checkpoints import open_checkpointer
from medical_agent.java_client import CallCredentials
from medical_agent.kb.chunking import chunk_document
from medical_agent.kb.retrieval import Retriever
from medical_agent.kb.store import KnowledgeBase
from medical_agent.providers.base import ProviderResponse
from medical_agent.schemas.run import RunStartRequest
from medical_agent.worker import AgentWorker

CORPUS = Path(__file__).parent.parent / "kb"


@pytest.fixture()
def retriever() -> Retriever:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    return Retriever(knowledge.chunks())


class StubProvider:
    def invoke_now(self, request):  # type: ignore[no-untyped-def]
        return ProviderResponse(
            text='{"intents": ["BILLING"], "risk": "ROUTINE", "confidence": 0.9}',
            provider="stub", model="stub-1", latency_ms=0)


def credentials() -> CallCredentials:
    return CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                           outbound_token="out")


def run_with(retriever: Retriever, content: str, tmp_path: Path, run_id: int = 21) -> dict:
    with open_checkpointer(f"sqlite://{tmp_path / 'r.sqlite'}") as checkpointer:
        worker = AgentWorker(StubProvider(), checkpointer, lambda state: None, retriever=retriever)
        return worker.process(
            RunStartRequest(run_id=run_id, message_id=run_id * 10, patient_id=100,
                            conversation_revision=2, execution_generation=1, content=content,
                            request_id=f"req-retrieval-{run_id}"),
            credentials(),
        )


def test_a_policy_question_finds_its_policy(retriever: Retriever) -> None:
    result = retriever.search("Where do I send my bill payment?")

    assert not result.empty
    assert result.hits[0].chunk.document_id == "billing-and-insurance"
    assert result.hits[0].chunk.title == "Billing and insurance policy"


def test_a_refill_question_finds_the_refill_policy(retriever: Retriever) -> None:
    result = retriever.search("Can I get a refill of my prescription?")

    assert result.hits[0].chunk.document_id == "prescription-refills"


def test_a_question_with_no_shared_vocabulary_finds_nothing(retriever: Retriever) -> None:
    result = retriever.search("What is the airspeed of a swallow?")

    assert result.empty
    assert result.hits == ()


def test_an_empty_query_finds_nothing(retriever: Retriever) -> None:
    assert retriever.search("   ").empty


def test_the_limit_is_respected(retriever: Retriever) -> None:
    assert len(retriever.search("prescription refill", limit=2).hits) <= 2


def test_both_baselines_are_reported(retriever: Retriever) -> None:
    diagnostics = retriever.search("prescription refill").diagnostics

    assert diagnostics["lexical"], "the lexical baseline must be visible"
    assert diagnostics["dense"], "the dense baseline must be visible"
    assert diagnostics["rrf_k"] == 60
    assert diagnostics["embedding_version"].startswith("hash")
    assert diagnostics["documents"]


def test_the_dense_baseline_never_adds_an_unmatched_chunk(retriever: Retriever) -> None:
    # With a hashing embedding, "dense" can only re-rank what the lexical baseline found.
    result = retriever.search("refill")
    lexical_ids = set(result.diagnostics["lexical_support"])

    assert {hit.chunk.id for hit in result.hits} <= lexical_ids


def test_a_tie_between_two_documents_is_reported() -> None:
    shared = "## Payment\nA payment plan changes the schedule, not the amount owed."
    # The same title and body, so the two chunks score identically: only the document differs.
    first = chunk_document("billing-a", 1, "Billing policy", shared)
    second = chunk_document("billing-b", 1, "Billing policy", shared)

    result = Retriever((*first, *second)).search("payment plan")

    assert result.conflict, "two different documents ranked identically must not be picked between"
    assert {hit.chunk.document_id for hit in result.hits} == {"billing-a", "billing-b"}


def test_a_withdrawn_corpus_retrieves_nothing(tmp_path: Path) -> None:
    corpus = tmp_path / "kb"
    shutil.copytree(CORPUS, corpus)
    knowledge = KnowledgeBase(corpus / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    knowledge.withdraw(version.id)

    assert Retriever(knowledge.chunks()).search("billing").empty


def test_the_graph_uses_retrieval_and_marks_missing_evidence(tmp_path: Path) -> None:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    retriever = Retriever(knowledge.chunks())

    with_evidence = run_with(retriever, "Where do I send my bill payment?", tmp_path, 21)
    without = run_with(retriever, "What is the airspeed of a swallow?", tmp_path, 22)

    assert with_evidence["citations"]
    assert "evidence_missing" not in with_evidence["flags"]
    assert without["citations"] == []
    assert "evidence_missing" in without["flags"]
    assert "could not find" in without["draft"]
