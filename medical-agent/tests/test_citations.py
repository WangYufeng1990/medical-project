"""Round 56.3 — citations are built from evidence, not from the draft.

A citation carries the chunk id, document, version, heading path and content hash of a chunk
that retrieval returned. The tests pin both directions: every citation must match a retrieved
chunk, and anything else is refused.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from medical_agent.kb.citations import (
    Citation,
    CitationError,
    build_citations,
    describe,
    verify_citations,
)
from medical_agent.kb.retrieval import Retriever
from medical_agent.kb.store import KnowledgeBase

CORPUS = Path(__file__).parent.parent / "kb"


@pytest.fixture()
def retriever() -> Retriever:
    knowledge = KnowledgeBase(CORPUS / "manifest.json")
    version = knowledge.ingest()
    knowledge.publish(version.id)
    return Retriever(knowledge.chunks())


def test_a_citation_describes_a_retrieved_chunk(retriever: Retriever) -> None:
    result = retriever.search("Can I get a refill?")
    citations = build_citations(result.hits)

    assert citations
    retrieved = {hit.chunk.id: hit.chunk for hit in result.hits}
    for citation in citations:
        chunk = retrieved[citation.chunk_id]
        assert citation.document_id == chunk.document_id
        assert citation.document_version == chunk.document_version
        assert citation.content_hash == chunk.content_hash
        assert citation.heading_path


@pytest.mark.parametrize("forged_id", ["refill-policy:9:999", "unknown:1:000"])
def test_a_citation_that_was_not_retrieved_is_refused(retriever: Retriever, forged_id: str) -> None:
    result = retriever.search("Can I get a refill?")
    citations = build_citations(result.hits)
    forged = Citation(chunk_id=forged_id, document_id="billing-and-insurance",
                      document_version=1, title="Billing and insurance policy",
                      heading_path=("Billing",), content_hash="0" * 64)

    with pytest.raises(CitationError, match="not retrieved"):
        verify_citations((*citations, forged), result.hits)


def test_a_citation_with_a_changed_hash_is_refused(retriever: Retriever) -> None:
    result = retriever.search("Can I get a refill?")
    citation = build_citations(result.hits)[0]
    tampered = Citation(chunk_id=citation.chunk_id, document_id=citation.document_id,
                        document_version=citation.document_version, title=citation.title,
                        heading_path=citation.heading_path, content_hash="f" * 64)

    with pytest.raises(CitationError, match="does not match"):
        verify_citations((tampered,), result.hits)


def test_citations_are_deduplicated_and_limited(retriever: Retriever) -> None:
    result = retriever.search("prescription refill", limit=6)
    citations = build_citations(result.hits, limit=3)

    assert len(citations) <= 3
    assert len({citation.chunk_id for citation in citations}) == len(citations)


def test_describe_names_the_title_and_heading(retriever: Retriever) -> None:
    result = retriever.search("Where do I send my bill payment?")
    text = describe(build_citations(result.hits))

    assert "Billing and insurance policy" in text
    assert "(" in text and ")" in text
