"""The knowledge base's manifest contract (Round 56.2).

A document becomes usable only by appearing in the manifest with an approval recorded and a
matching content hash. Everything else — a file with no manifest entry, an unapproved entry,
a hash that no longer matches the text — is unavailable, and the loader says which.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Annotated, Final, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

MANIFEST_VERSION: Final[Literal["v1"]] = "v1"


class DocumentStatus(StrEnum):
    APPROVED = "approved"
    WITHDRAWN = "withdrawn"


class DocumentEntry(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    id: Annotated[str, Field(pattern=r"^[a-z0-9][a-z0-9-]{1,63}$")]
    title: Annotated[str, Field(min_length=3, max_length=200)]
    version: Annotated[int, Field(strict=True, ge=1)]
    status: DocumentStatus
    approved_by: Annotated[str, Field(min_length=1, max_length=64)]
    approved_at: Annotated[str, Field(pattern=r"^\d{4}-\d{2}-\d{2}$")]
    tags: Annotated[list[str], Field(min_length=1, max_length=16)] = Field(default_factory=list)
    file: Annotated[str, Field(pattern=r"^documents/[a-z0-9-]+\.md$")]
    sha256: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]

    @model_validator(mode="after")
    def approved_entries_name_their_approver(self) -> DocumentEntry:
        if self.status is DocumentStatus.APPROVED and not self.approved_by.strip():
            raise ValueError("an approved document must name who approved it")
        return self


class Manifest(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    manifest_version: Literal["v1"] = MANIFEST_VERSION
    corpus: Annotated[str, Field(min_length=1, max_length=500)]
    documents: Annotated[list[DocumentEntry], Field(min_length=1, max_length=500)]

    @model_validator(mode="after")
    def ids_are_unique(self) -> Manifest:
        ids = [document.id for document in self.documents]
        if len(ids) != len(set(ids)):
            raise ValueError("document ids must be unique")
        return self
