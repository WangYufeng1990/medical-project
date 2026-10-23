"""Round 56.2 — knowledge base ingestion and release.

The manifest is the approval record, so these tests are about what the knowledge base refuses:
repeated ingestion must not duplicate anything, an unapproved or tampered document must not be
usable, and a withdrawn approval must take the published version out of service.
"""

from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

import pytest

from medical_agent.kb.chunking import MAX_CHUNK_CHARS, chunk_document
from medical_agent.kb.embedding import DIMENSIONS, EMBEDDING_VERSION, embed
from medical_agent.kb.loader import CorpusError, load_corpus
from medical_agent.kb.store import KnowledgeBase, VersionStatus

CORPUS = Path(__file__).parent.parent / "kb"


def copy_corpus(tmp_path: Path) -> Path:
    target = tmp_path / "kb"
    shutil.copytree(CORPUS, target)
    return target / "manifest.json"


def edit_manifest(path: Path, edit) -> None:  # type: ignore[no-untyped-def]
    manifest = json.loads(path.read_text())
    edit(manifest)
    path.write_text(json.dumps(manifest, indent=2) + "\n")


def withdraw(manifest: dict, document_id: str) -> None:
    for document in manifest["documents"]:
        if document["id"] == document_id:
            document["status"] = "withdrawn"


def test_repeated_ingestion_is_idempotent(tmp_path: Path) -> None:
    kb = KnowledgeBase(copy_corpus(tmp_path))

    first = kb.ingest()
    second = kb.ingest()

    assert first.id == second.id
    assert len(kb.versions()) == 1
    assert [chunk.id for chunk in first.chunks] == [chunk.id for chunk in second.chunks]
    assert [chunk.content_hash for chunk in first.chunks] == [
        chunk.content_hash for chunk in second.chunks
    ]


def test_an_unapproved_document_is_not_available(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    edit_manifest(manifest, lambda m: withdraw(m, "lab-results"))

    kb = KnowledgeBase(manifest)
    version = kb.ingest()

    assert "lab-results" not in version.document_ids
    assert all(chunk.document_id != "lab-results" for chunk in version.chunks)
    corpus = load_corpus(manifest)
    assert any(reason == "lab-results: withdrawn" for reason in corpus.skipped)


def test_a_file_without_a_manifest_entry_is_ignored(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    (manifest.parent / "documents" / "unapproved-note.md").write_text("# Unapproved\nsecret\n")

    version = KnowledgeBase(manifest).ingest()

    assert "unapproved-note" not in version.document_ids
    assert all("secret" not in chunk.text for chunk in version.chunks)


def test_a_tampered_document_is_refused(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    (manifest.parent / "documents" / "appointments.md").write_text("# Appointment policy\nEdited\n")

    with pytest.raises(CorpusError, match="approved hash"):
        KnowledgeBase(manifest).ingest()


def test_a_withdrawn_version_cannot_be_published(tmp_path: Path) -> None:
    kb = KnowledgeBase(copy_corpus(tmp_path))
    version = kb.ingest()
    kb.withdraw(version.id)

    with pytest.raises(CorpusError, match="withdrawn version"):
        kb.publish(version.id)


def test_withdrawing_a_document_takes_the_published_version_out_of_service(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    kb = KnowledgeBase(manifest)
    version = kb.ingest()
    kb.publish(version.id)
    assert len(kb.chunks()) == version.chunk_count

    edit_manifest(manifest, lambda m: withdraw(m, "billing-and-insurance"))

    assert kb.active() is None, "a version whose approval was withdrawn must not stay active"
    assert kb.chunks() == ()
    with pytest.raises(CorpusError, match="no longer approved"):
        kb.publish(version.id)


def test_a_reapproved_document_invalidates_the_old_release(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    kb = KnowledgeBase(manifest)
    version = kb.ingest()
    kb.publish(version.id)
    assert kb.chunks()

    # The document is approved again at a new version: the old release was built from the old
    # approval and must not keep serving it.
    edit_manifest(manifest, lambda m: m["documents"][0].update({"version": 2}))

    assert kb.active() is None
    assert kb.chunks() == ()
    with pytest.raises(CorpusError, match="re-approved"):
        kb.publish(version.id)


def test_publishing_a_new_version_deactivates_the_previous_one(tmp_path: Path) -> None:
    manifest = copy_corpus(tmp_path)
    kb = KnowledgeBase(manifest)
    first = kb.ingest()
    kb.publish(first.id)

    extra = manifest.parent / "documents" / "portals.md"
    extra.write_text("# Portal policy\n\n## Access\nPortal access is personal.\n")
    edit_manifest(manifest, lambda m: m["documents"].append({
        "id": "portals",
        "title": "Portal policy",
        "version": 1,
        "status": "approved",
        "approved_by": "practice-manager",
        "approved_at": "2026-10-21",
        "tags": ["portal"],
        "file": "documents/portals.md",
        "sha256": hashlib.sha256(extra.read_bytes()).hexdigest(),
    }))

    second = kb.ingest()
    kb.publish(second.id)

    published = [record for record in kb.versions() if record.status is VersionStatus.PUBLISHED]
    assert [record.id for record in published] == [second.id]
    assert all(chunk.document_id != "portals" for chunk in first.chunks)
    assert any(chunk.document_id == "portals" for chunk in kb.chunks())


def test_chunks_are_title_aware_and_bounded() -> None:
    body = (CORPUS / "documents" / "appointments.md").read_text()
    chunks = chunk_document("appointments", 1, "Appointment scheduling policy", body)

    assert chunks, "a document must produce chunks"
    for chunk in chunks:
        assert chunk.text.startswith("Appointment scheduling policy — ")
        assert chunk.heading_path
        assert len(chunk.id.split(":")) == 3
        assert len(chunk.content_hash) == 64
        assert len(chunk.text) <= MAX_CHUNK_CHARS, "the prefix counts toward the limit"


def test_the_corpus_is_synthetic_and_small() -> None:
    manifest = json.loads((CORPUS / "manifest.json").read_text())

    assert "synthetic" in manifest["corpus"]
    assert 1 <= len(manifest["documents"]) <= 10
    words = sum(len((CORPUS / document["file"]).read_text().split())
                for document in manifest["documents"])
    assert words < 2000, "the corpus is deliberately small"


def test_the_embedding_baseline_is_deterministic_and_labelled() -> None:
    first = embed("appointment scheduling policy")
    second = embed("appointment scheduling policy")

    assert first == second
    assert len(first) == DIMENSIONS
    assert EMBEDDING_VERSION.startswith("hash")
    assert embed("") == [0.0] * DIMENSIONS
