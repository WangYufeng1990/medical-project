"""The Java ⇄ Python contract for an agent run (Round 55.1).

These models are the single source of truth: the committed JSON Schemas under
``contracts/agent/v1/`` are generated from them, and a test fails when the files and
the models drift. Java validates the same fixtures, so both sides read one contract.

``extra="forbid"`` everywhere is deliberate. A field the contract does not define is
a caller that believes it is sending something it is not, and accepting it silently
would hide that.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Final, Literal

from pydantic import BaseModel, ConfigDict, Field

CONTRACT_VERSION: Final[Literal["v1"]] = "v1"


class RunState(StrEnum):
    """The run path and its exceptional terminals.

    ``WAITING_REVIEW`` and ``COMPLETED`` are distinct on purpose: a review decision
    and the run that produced the draft finish separately (Round 57).
    """

    PENDING = "PENDING"
    RUNNING = "RUNNING"
    WAITING_REVIEW = "WAITING_REVIEW"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    SUPERSEDED = "SUPERSEDED"


class RunError(BaseModel):
    """A machine-readable reason a run did not produce a draft."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    code: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=500)


class RunStartRequest(BaseModel):
    """A start or continue request from the Java dispatcher.

    The patient identity comes from Java's own run context; the service never takes
    it from anything a model produced (the authorization contract keeps that true in
    55.3).
    """

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    run_id: int = Field(gt=0)
    message_id: int = Field(gt=0)
    patient_id: int = Field(gt=0)
    conversation_revision: int = Field(ge=1)
    sender_type: Literal["PATIENT"] = "PATIENT"
    content: str = Field(min_length=1, max_length=4000)
    locale: str = Field(default="en", min_length=2, max_length=16)
    request_id: str = Field(min_length=8, max_length=64)


class RunStatusResponse(BaseModel):
    """What Java may learn about a run without asking for model output."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    run_id: int = Field(gt=0)
    state: RunState
    revision: int = Field(ge=0)
    updated_at: datetime
    error: RunError | None = None


class ErrorResponse(BaseModel):
    """The error envelope every internal endpoint returns."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    code: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=500)
    request_id: str | None = Field(default=None, max_length=64)
