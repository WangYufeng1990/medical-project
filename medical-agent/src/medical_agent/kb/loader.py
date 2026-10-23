"""Loading an approved corpus (Round 56.2).

The manifest is the approval record. A document is loaded only when it is listed, approved and
its bytes still hash to the recorded value; anything else is refused or reported as skipped
with a reason, never silently included.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from pathlib import Path

from medical_agent.kb.models import DocumentEntry, DocumentStatus, Manifest


class CorpusError(RuntimeError):
    """The corpus cannot be loaded as approved."""


@dataclass(frozen=True)
class LoadedDocument:
    entry: DocumentEntry
    body: str


@dataclass(frozen=True)
class Corpus:
    documents: tuple[LoadedDocument, ...]
    skipped: tuple[str, ...] = field(default_factory=tuple)


def load_manifest(path: Path) -> Manifest:
    try:
        return Manifest.model_validate_json(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise CorpusError(f"the manifest cannot be read: {path}") from exc


def load_corpus(manifest_path: Path) -> Corpus:
    manifest = load_manifest(manifest_path)
    base = manifest_path.parent
    loaded: list[LoadedDocument] = []
    skipped: list[str] = []
    for entry in manifest.documents:
        if entry.status is not DocumentStatus.APPROVED:
            skipped.append(f"{entry.id}: {entry.status.value}")
            continue
        path = base / entry.file
        try:
            raw = path.read_bytes()
        except OSError as exc:
            raise CorpusError(f"{entry.id}: the approved file is missing ({entry.file})") from exc
        if hashlib.sha256(raw).hexdigest() != entry.sha256:
            raise CorpusError(
                f"{entry.id}: the file does not match the approved hash; it must be approved again"
            )
        loaded.append(LoadedDocument(entry=entry, body=raw.decode("utf-8")))
    if not loaded:
        raise CorpusError("no approved document is available")
    return Corpus(documents=tuple(loaded), skipped=tuple(skipped))
