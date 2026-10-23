"""Server-built citations (Round 56.3).

A citation is derived from the chunk that retrieval returned — its id, document, version,
heading path and content hash. Nothing here reads the model's text, so a draft cannot invent a
reference, and a citation that does not correspond to a retrieved chunk is refused.
"""

from __future__ import annotations

from dataclasses import dataclass

from medical_agent.kb.retrieval import Hit


class CitationError(RuntimeError):
    """A citation does not describe retrieved evidence."""


@dataclass(frozen=True)
class Citation:
    chunk_id: str
    document_id: str
    document_version: int
    title: str
    heading_path: tuple[str, ...]
    content_hash: str


def build_citations(hits: tuple[Hit, ...], limit: int = 3) -> tuple[Citation, ...]:
    citations: list[Citation] = []
    seen: set[str] = set()
    for hit in hits:
        chunk = hit.chunk
        if chunk.id in seen:
            continue
        seen.add(chunk.id)
        citations.append(
            Citation(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                document_version=chunk.document_version,
                title=chunk.title,
                heading_path=chunk.heading_path,
                content_hash=chunk.content_hash,
            )
        )
        if len(citations) == limit:
            break
    return tuple(citations)


def verify_citations(citations: tuple[Citation, ...], hits: tuple[Hit, ...]) -> None:
    """Refuse a citation that is not one of the retrieved chunks, hash included."""
    retrieved = {hit.chunk.id: hit.chunk for hit in hits}
    for citation in citations:
        chunk = retrieved.get(citation.chunk_id)
        if chunk is None:
            raise CitationError(f"{citation.chunk_id} was not retrieved for this draft")
        if chunk.content_hash != citation.content_hash:
            raise CitationError(f"{citation.chunk_id} does not match the retrieved content")


def describe(citations: tuple[Citation, ...]) -> str:
    """A short, human-readable source line built from the citations themselves."""
    return "; ".join(
        f"{citation.title} ({'/'.join(citation.heading_path)})" for citation in citations
    )
