"""Retrieval: lexical, dense and a hybrid of the two (Round 56.3).

The lexical baseline is BM25, the dense baseline is the hashing embedding from 56.2, and the
hybrid is reciprocal rank fusion. All three are measured and reported together: the plan asks
for a baseline comparison, not for a hand-picked winner, and a fused list that nobody can
explain is worse than a diagnostic that shows the inputs.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from medical_agent.kb.chunking import Chunk
from medical_agent.kb.embedding import EMBEDDING_VERSION, embed

BM25_K1 = 1.2
BM25_B = 0.75
RRF_K = 60

_TOKEN = re.compile(r"[a-z0-9']+")
_STOPWORDS = frozenset(
    [
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
        "has", "have", "how", "i", "if", "in", "into", "is", "it", "its", "my", "of", "on",
        "or", "the", "their", "there", "to", "was", "we", "what", "when", "where", "which",
        "who", "why", "with", "you", "your",
    ]
)


def tokenize(text: str) -> list[str]:
    """Content tokens: lowercase words without the small set that carries no topic."""
    return [token for token in _TOKEN.findall(text.lower()) if token not in _STOPWORDS]


@dataclass(frozen=True)
class Hit:
    chunk: Chunk
    score: float
    source: str


@dataclass(frozen=True)
class SearchResult:
    hits: tuple[Hit, ...]
    diagnostics: dict[str, Any]
    # Two different documents ranked the same for this query: the caller must not pick one.
    conflict: bool = False

    @property
    def empty(self) -> bool:
        return not self.hits


class Retriever:
    """An index over the approved corpus, re-validated on every search.

    The source can be a snapshot or a callable (``KnowledgeBase.chunks``), which is what the
    service passes: a long-lived retriever must follow a withdrawal without a restart, so each
    search re-reads the source and rebuilds the index when what it holds has changed.
    """

    def __init__(
        self,
        chunks: tuple[Chunk, ...] | Callable[[], tuple[Chunk, ...]],
        embedding_version: str = EMBEDDING_VERSION,
    ) -> None:
        self._source: Callable[[], tuple[Chunk, ...]] = (
            chunks if callable(chunks) else (lambda: chunks)
        )
        self._embedding_version = embedding_version
        self._signature: tuple[tuple[str, str], ...] | None = None
        self._chunks: tuple[Chunk, ...] = ()
        self._chunks_by_id: dict[str, Chunk] = {}
        self._tokens: dict[str, list[str]] = {}
        self._lengths: dict[str, int] = {}
        self._average_length = 0.0
        self._document_frequency: Counter[str] = Counter()
        self._vectors: dict[str, list[float]] = {}
        self._refresh()

    def _refresh(self) -> None:
        chunks = tuple(self._source())
        signature = tuple((chunk.id, chunk.content_hash) for chunk in chunks)
        if signature == self._signature:
            return
        self._signature = signature
        self._chunks = chunks
        self._chunks_by_id = {chunk.id: chunk for chunk in chunks}
        self._tokens = {chunk.id: tokenize(chunk.text) for chunk in chunks}
        self._lengths = {chunk_id: len(tokens) for chunk_id, tokens in self._tokens.items()}
        self._average_length = (
            sum(self._lengths.values()) / len(self._lengths) if self._lengths else 0.0
        )
        self._document_frequency = Counter()
        for tokens in self._tokens.values():
            self._document_frequency.update(set(tokens))
        self._vectors = {
            chunk.id: embed(" ".join(self._tokens[chunk.id])) for chunk in chunks
        }

    def search(self, query: str, limit: int = 4) -> SearchResult:
        self._refresh()
        tokens = tokenize(query)
        if not tokens:
            return SearchResult((), {"query_tokens": tokens, "reason": "no query"})
        if not self._chunks:
            return SearchResult((), {"query_tokens": tokens,
                                     "reason": "no approved corpus is published"})

        lexical = self._lexical_scores(tokens)
        conflict = _tied_documents(lexical, self._chunks_by_id)
        # The dense baseline only re-ranks chunks the lexical baseline found. With a hashing
        # embedding there is no semantics to stand on: on its own it matches on shared words
        # and would "retrieve" something for any query at all. A real embedding model can
        # relax this rule; the diagnostics keep both lists visible either way.
        dense = {
            chunk_id: score
            for chunk_id, score in self._dense_scores(" ".join(tokens)).items()
            if chunk_id in lexical
        }
        fused = self._rrf(lexical, dense)

        ranked = sorted(fused.items(), key=lambda item: (-item[1], item[0]))[:limit]
        hits = tuple(
            Hit(chunk=self._chunks_by_id[chunk_id], score=score, source="hybrid")
            for chunk_id, score in ranked
        )
        diagnostics = {
            "query_tokens": tokens,
            "embedding_version": self._embedding_version,
            "rrf_k": RRF_K,
            "lexical": self._top(lexical, limit),
            # Every chunk the lexical baseline matched, so a caller can check that the dense
            # baseline never introduced one of its own.
            "lexical_support": sorted(lexical),
            "dense": self._top(dense, limit),
            "documents": sorted({hit.chunk.document_id for hit in hits}),
        }
        return SearchResult(hits, diagnostics, conflict=conflict)

    # ── baselines ──────────────────────────────────────────────────

    def _lexical_scores(self, tokens: list[str]) -> dict[str, float]:
        scores: dict[str, float] = {}
        total = len(self._chunks)
        for chunk_id, chunk_tokens in self._tokens.items():
            counts = Counter(chunk_tokens)
            length = self._lengths[chunk_id]
            score = 0.0
            for token in tokens:
                frequency = counts.get(token, 0)
                if not frequency:
                    continue
                appearances = self._document_frequency.get(token, 0)
                idf = math.log(1 + (total - appearances + 0.5) / (appearances + 0.5))
                denominator = frequency + BM25_K1 * (
                    1 - BM25_B + BM25_B * (length / (self._average_length or 1.0))
                )
                score += idf * (frequency * (BM25_K1 + 1)) / denominator
            if score > 0:
                scores[chunk_id] = score
        return scores

    def _dense_scores(self, query: str) -> dict[str, float]:
        query_vector = embed(query)
        scores: dict[str, float] = {}
        for chunk_id, vector in self._vectors.items():
            score = sum(a * b for a, b in zip(vector, query_vector, strict=True))
            if score > 0:
                scores[chunk_id] = score
        return scores

    @staticmethod
    def _rrf(lexical: dict[str, float], dense: dict[str, float]) -> dict[str, float]:
        fused: dict[str, float] = {}
        for ranking in (lexical, dense):
            ordered = sorted(ranking.items(), key=lambda item: (-item[1], item[0]))
            for rank, (chunk_id, _score) in enumerate(ordered, start=1):
                fused[chunk_id] = fused.get(chunk_id, 0.0) + 1.0 / (RRF_K + rank)
        return fused


    @staticmethod
    def _top(scores: dict[str, float], limit: int) -> list[tuple[str, float]]:
        ordered = sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:limit]
        return [(chunk_id, round(score, 4)) for chunk_id, score in ordered]


def _tied_documents(scores: dict[str, float], by_id: dict[str, Chunk]) -> bool:
    """True when the *whole* best score group spans more than one document.

    An exact tie is the only disagreement these baselines can detect without pretending to
    understand the text. Comparing only the first two chunks would miss "A, A, B" where two
    chunks of one document come first, so the check looks at every chunk sharing the top score.
    """
    if not scores:
        return False
    best = max(scores.values())
    tied = {by_id[chunk_id].document_id for chunk_id, score in scores.items()
            if abs(score - best) < 1e-9}
    return len(tied) > 1
