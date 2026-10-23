"""Title-aware chunking (Round 56.2).

Each section of a Markdown document becomes one or more chunks that carry the document title
and the heading path, so a retrieved chunk is self-describing and its citation is built from
data rather than from the model's memory. Chunk ids and hashes are stable: re-ingesting the
same text produces the same identifiers, which is what makes ingestion idempotent.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass

MAX_CHUNK_CHARS = 600
HEADING = re.compile(r"^(#{1,6})\s+(.*)$")


@dataclass(frozen=True)
class Chunk:
    id: str
    document_id: str
    document_version: int
    title: str
    heading_path: tuple[str, ...]
    text: str
    content_hash: str


def chunk_document(document_id: str, version: int, title: str, body: str) -> list[Chunk]:
    chunks: list[Chunk] = []
    headings: list[str] = []
    current: list[str] = []

    def flush() -> None:
        text = "\n".join(line for line in current if line.strip()).strip()
        current.clear()
        if not text:
            return
        path = tuple(headings) if headings else (title,)
        for piece in _split(text, MAX_CHUNK_CHARS):
            chunk_text = f"{title} — {' / '.join(path)}\n{piece}"
            chunk_id = f"{document_id}:{version}:{len(chunks):03d}"
            chunks.append(
                Chunk(
                    id=chunk_id,
                    document_id=document_id,
                    document_version=version,
                    title=title,
                    heading_path=path,
                    text=chunk_text,
                    content_hash=hashlib.sha256(chunk_text.encode()).hexdigest(),
                )
            )

    for line in body.splitlines():
        match = HEADING.match(line)
        if match:
            flush()
            level = len(match.group(1))
            headings[:] = headings[: level - 1]
            headings.append(match.group(2).strip())
            continue
        current.append(line)
    flush()
    return chunks


def _split(text: str, limit: int) -> list[str]:
    if len(text) <= limit:
        return [text]
    pieces: list[str] = []
    remaining = text
    while len(remaining) > limit:
        cut = remaining.rfind(". ", 0, limit)
        if cut == -1:
            cut = remaining.rfind("\n", 0, limit)
        if cut == -1:
            cut = limit
        else:
            cut += 1
        pieces.append(remaining[:cut].strip())
        remaining = remaining[cut:].strip()
    if remaining:
        pieces.append(remaining)
    return pieces
