"""Round 56.3 — retrieval baselines and the hybrid.

The corpus is small on purpose, so these tests are about behaviour that must hold regardless of
size: a policy question finds its policy, a question with no shared vocabulary finds nothing (and
says so), the two baselines are both reported, and a tie between two documents is not resolved
by silently picking one.
"""

from __future__ import annotations

import hashlib
import shutil
import threading
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


def run_with(retriever: Retriever | None, content: str, tmp_path: Path, run_id: int = 21) -> dict:
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


def test_a_long_lived_retriever_stops_serving_a_withdrawn_version(tmp_path: Path) -> None:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    live = Retriever(knowledge.chunks)  # what the application builds: a live view
    assert live.search("bill payment").hits

    knowledge.withdraw(version.id)

    assert live.search("bill payment").empty, "a withdrawal must apply without a restart"
    assert live.search("bill payment").diagnostics["reason"] == "no approved corpus is published"


def test_an_unpublished_knowledge_base_searches_nothing(tmp_path: Path) -> None:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    knowledge.ingest()  # staged, never published
    live = Retriever(knowledge.chunks)

    assert live.search("bill payment").empty


def test_a_tie_between_two_documents_is_reported() -> None:
    shared = "## Payment\nA payment plan changes the schedule, not the amount owed."
    # The same title and body, so the two chunks score identically: only the document differs.
    first = chunk_document("billing-a", 1, "Billing policy", shared)
    second = chunk_document("billing-b", 1, "Billing policy", shared)

    result = Retriever((*first, *second)).search("payment plan")

    assert result.conflict, "two different documents ranked identically must not be picked between"
    assert {hit.chunk.document_id for hit in result.hits} == {"billing-a", "billing-b"}


def test_a_tie_across_a_document_pair_is_a_conflict() -> None:
    # A, A, B share the top score: comparing only the first two chunks would miss the B.
    from medical_agent.kb.chunking import Chunk

    text = "Billing policy — Billing policy / Payment\nA payment plan changes the schedule."
    digest = hashlib.sha256(text.encode()).hexdigest()

    def chunk(chunk_id: str, document_id: str) -> Chunk:
        return Chunk(id=chunk_id, document_id=document_id, document_version=1,
                     title="Billing policy", heading_path=("Payment",), text=text,
                     content_hash=digest)

    result = Retriever(
        (chunk("billing-a:1:000", "billing-a"), chunk("billing-a:1:001", "billing-a"),
         chunk("billing-b:1:000", "billing-b"))
    ).search("payment plan")

    assert result.conflict
    assert {hit.chunk.document_id for hit in result.hits} == {"billing-a", "billing-b"}


def test_a_tie_inside_one_document_is_not_a_conflict() -> None:
    body = "## Payment\nA payment plan changes the schedule, not the amount owed."
    first = chunk_document("billing-a", 1, "Billing policy", body)
    second = chunk_document("billing-a", 1, "Billing policy", "## Other\n" + body)

    assert Retriever((*first, *second)).search("payment plan").conflict is False


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


def test_a_run_without_a_retriever_is_flagged_as_missing_evidence(tmp_path: Path) -> None:
    # No manifest, or no published knowledge base: the draft must not fall back to a template
    # that reads as if it knew a policy.
    state = run_with(None, "Where do I send my bill payment?", tmp_path, 23)  # type: ignore[arg-type]

    assert state["evidence"] == []
    assert "evidence_missing" in state["flags"]
    assert "could not find" in state["draft"]


class PausingRetriever(Retriever):
    """Pauses the first search after its lexical scores, so another search can swap the index."""

    def __init__(self, source):  # type: ignore[no-untyped-def]
        self.paused = threading.Event()
        self.resume = threading.Event()
        self._should_pause = True
        super().__init__(source)

    def _lexical_scores(self, tokens, index):  # type: ignore[no-untyped-def]
        scores = super()._lexical_scores(tokens, index)
        if self._should_pause:
            self._should_pause = False
            self.paused.set()
            self.resume.wait(timeout=10)
        return scores


def _payment_corpus(document_id: str, heading: str) -> tuple:
    from medical_agent.kb.chunking import chunk_document

    body = f"## {heading}\nA payment plan changes the schedule, not the amount owed."
    return tuple(chunk_document(document_id, 1, f"{document_id} policy", body))


def test_a_search_in_flight_keeps_the_index_it_started_with() -> None:
    # The race the review found: a search used the new index with ids from the old one.
    old = _payment_corpus("old-policy", "Payment")
    new = _payment_corpus("new-policy", "Payment")
    holder = {"chunks": old}
    retriever = PausingRetriever(lambda: holder["chunks"])
    errors: list[BaseException] = []
    results: dict[str, object] = {}

    def first() -> None:
        try:
            results["first"] = retriever.search("payment plan")
        except BaseException as exc:
            errors.append(exc)

    first_thread = threading.Thread(target=first)
    first_thread.start()
    assert retriever.paused.wait(timeout=10), "the first search must reach the pause"

    holder["chunks"] = new
    results["second"] = retriever.search("payment plan")
    retriever.resume.set()
    first_thread.join(timeout=10)

    assert not errors, f"a concurrent swap raised: {errors}"
    first_documents = {hit.chunk.document_id for hit in results["first"].hits}  # type: ignore[union-attr]
    second_documents = {hit.chunk.document_id for hit in results["second"].hits}  # type: ignore[union-attr]
    assert first_documents == {"old-policy"}, "the search in flight must finish on its own index"
    assert second_documents == {"new-policy"}, "a later search must see the new index"


def test_concurrent_searches_never_mix_two_corpora() -> None:
    old = _payment_corpus("old-policy", "Payment")
    new = _payment_corpus("new-policy", "Payment")
    holder = {"chunks": old}
    retriever = Retriever(lambda: holder["chunks"])
    errors: list[BaseException] = []
    seen: list[set[str]] = []
    stop = threading.Event()

    def flipper() -> None:
        while not stop.is_set():
            holder["chunks"] = new if holder["chunks"] is old else old

    def searcher() -> None:
        while not stop.is_set():
            try:
                result = retriever.search("payment plan")
                seen.append({hit.chunk.document_id for hit in result.hits})
            except BaseException as exc:
                errors.append(exc)

    threads = [
        threading.Thread(target=flipper),
        *(threading.Thread(target=searcher) for _ in range(4)),
    ]
    for thread in threads:
        thread.start()
    threading.Event().wait(0.5)
    stop.set()
    for thread in threads:
        thread.join(timeout=10)

    assert not errors, f"concurrent searches raised: {errors[:3]}"
    assert seen, "the searches must have produced results"
    assert all(documents <= {"old-policy", "new-policy"} for documents in seen)
    assert all(len(documents) == 1 for documents in seen), "one result, one corpus"
