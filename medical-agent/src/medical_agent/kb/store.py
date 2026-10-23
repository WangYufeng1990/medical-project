"""Versioned knowledge base (Round 56.2).

Ingestion builds a version, publishing makes one active, withdrawing takes it out of service.
A version is published only if every document it contains is *still* approved with the same
hash, so an approval that was later withdrawn cannot be revived by publishing an old version.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import StrEnum
from pathlib import Path

from medical_agent.kb.chunking import Chunk, chunk_document
from medical_agent.kb.embedding import EMBEDDING_VERSION
from medical_agent.kb.loader import Corpus, CorpusError, load_corpus, load_manifest
from medical_agent.kb.models import DocumentStatus


class VersionStatus(StrEnum):
    STAGED = "staged"
    PUBLISHED = "published"
    WITHDRAWN = "withdrawn"


@dataclass
class VersionRecord:
    id: str
    created_at: str
    document_ids: tuple[str, ...]
    chunk_count: int
    status: VersionStatus = VersionStatus.STAGED
    chunks: tuple[Chunk, ...] = field(default_factory=tuple)


class KnowledgeBase:
    def __init__(self, manifest_path: Path) -> None:
        self._manifest_path = manifest_path
        self._versions: dict[str, VersionRecord] = {}
        self._active: str | None = None

    # ── ingestion ──────────────────────────────────────────────────

    def ingest(self) -> VersionRecord:
        corpus = load_corpus(self._manifest_path)
        digest = self._digest(corpus)
        if digest in self._versions:
            return self._versions[digest]

        chunks: list[Chunk] = []
        for document in corpus.documents:
            chunks.extend(
                chunk_document(document.entry.id, document.entry.version,
                               document.entry.title, document.body)
            )
        record = VersionRecord(
            id=digest,
            created_at=datetime.now(UTC).isoformat(timespec="seconds"),
            document_ids=tuple(document.entry.id for document in corpus.documents),
            chunk_count=len(chunks),
            chunks=tuple(chunks),
        )
        self._versions[digest] = record
        return record

    def _digest(self, corpus: Corpus) -> str:
        material = "|".join(
            f"{document.entry.id}@{document.entry.version}:{document.entry.sha256}"
            for document in corpus.documents
        )
        return hashlib.sha256(f"{EMBEDDING_VERSION}|{material}".encode()).hexdigest()

    # ── release ────────────────────────────────────────────────────

    def publish(self, version_id: str) -> VersionRecord:
        record = self._versions.get(version_id)
        if record is None:
            raise CorpusError(f"unknown version {version_id}")
        if record.status is VersionStatus.WITHDRAWN:
            raise CorpusError("a withdrawn version cannot be published again")
        self._require_still_approved(record)
        for other in self._versions.values():
            if other.status is VersionStatus.PUBLISHED:
                other.status = VersionStatus.STAGED
        record.status = VersionStatus.PUBLISHED
        self._active = record.id
        return record

    def withdraw(self, version_id: str) -> None:
        record = self._versions.get(version_id)
        if record is None:
            raise CorpusError(f"unknown version {version_id}")
        record.status = VersionStatus.WITHDRAWN
        if self._active == version_id:
            self._active = None

    # ── use ────────────────────────────────────────────────────────

    def active(self) -> VersionRecord | None:
        if self._active is None:
            return None
        record = self._versions[self._active]
        try:
            self._require_still_approved(record)
        except CorpusError:
            return None
        return record

    def chunks(self) -> tuple[Chunk, ...]:
        record = self.active()
        return record.chunks if record is not None else ()

    def versions(self) -> tuple[VersionRecord, ...]:
        return tuple(self._versions.values())

    def _require_still_approved(self, record: VersionRecord) -> None:
        manifest = load_manifest(self._manifest_path)
        approved = {
            entry.id: entry.sha256
            for entry in manifest.documents
            if entry.status is DocumentStatus.APPROVED
        }
        for document_id in record.document_ids:
            if document_id not in approved:
                raise CorpusError(f"document {document_id} is no longer approved")
