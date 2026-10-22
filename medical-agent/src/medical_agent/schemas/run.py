"""The Java ⇄ Python contract for an agent run (Round 55.1).

These models are the single source of truth: the committed JSON Schemas under
``contracts/agent/v1/`` are generated from them, and a test fails when the files and
the models drift. Java validates the same fixtures, so both sides read one contract.

``extra="forbid"`` everywhere is deliberate. A field the contract does not define is
a caller that believes it is sending something it is not, and accepting it silently
would hide that.
"""

from __future__ import annotations

import re
from datetime import datetime
from enum import StrEnum
from typing import Annotated, Any, Final, Literal

from pydantic import AwareDatetime, BaseModel, BeforeValidator, ConfigDict, Field

CONTRACT_VERSION: Final[Literal["v1"]] = "v1"

# Identity and revision fields are strict (Round 55.1 review): Pydantic's default mode
# coerces "123" to 123 and true to 1, so a payload the Java side refuses would be
# accepted here. The wire types are what the schema says they are.
StrictPositiveInt = Annotated[int, Field(strict=True, gt=0)]
StrictRevision = Annotated[int, Field(strict=True, ge=1)]
StrictCounter = Annotated[int, Field(strict=True, ge=0)]


# The offset needs its colon: Java's OffsetDateTime refuses "+0200", so accepting it here
# would be another input Python takes and Java rejects. The groups are used to range-check
# the offset, because datetime.fromisoformat() silently normalises an out-of-range one
# (+02:99 becomes +03:39).
_WIRE_TIMESTAMP = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,6})?)?"
    r"(?:[Zz]|(?P<sign>[+-])(?P<hours>\d{2}):(?P<minutes>\d{2}))$"
)


def _wire_timestamp(value: Any) -> Any:
    """Parse a timestamp the way both runtimes must agree on (Round 55.1 review).

    Java parses with ``OffsetDateTime.parse``, which needs an ISO-8601 ``T`` and an offset.
    Pydantic on its own also accepts an epoch number, a date-only string, a naive
    date-time and the space-separated spelling, so all of those are refused here. A
    ``datetime`` instance passes through, because the service builds its own responses in
    Python.
    """
    if isinstance(value, datetime):
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("a timestamp must have a timezone offset")
        return value
    if not isinstance(value, str):
        raise ValueError("a timestamp must be an ISO-8601 string with a timezone")
    match = _WIRE_TIMESTAMP.match(value)
    if match is None:
        raise ValueError(
            "a timestamp must be an ISO-8601 date-time with an offset, e.g. 2026-10-21T12:00:00Z"
        )
    if match.group("hours") is not None:
        hours, minutes = int(match.group("hours")), int(match.group("minutes"))
        # The range Java enforces: minutes 00-59, and at most 18:00 (18 only as 18:00).
        if minutes > 59:
            raise ValueError("a timestamp offset cannot have minutes above 59")
        if hours > 18 or (hours == 18 and minutes != 0):
            raise ValueError("a timestamp offset must be within +/-18:00")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00").replace("z", "+00:00"))
    except ValueError as exc:  # pragma: no cover - the pattern already bounds the shape
        raise ValueError("not a valid ISO-8601 date-time") from exc
    return parsed


# A wire timestamp: string, with a timezone, in the form both runtimes parse.
WireTimestamp = Annotated[datetime, AwareDatetime, BeforeValidator(_wire_timestamp)]


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
    run_id: StrictPositiveInt
    message_id: StrictPositiveInt
    patient_id: StrictPositiveInt
    conversation_revision: StrictRevision
    sender_type: Literal["PATIENT"] = "PATIENT"
    # The generation the capability is bound to; the worker echoes it when it submits a
    # result, which is what lets Java refuse a stale worker (Round 55.3/55.4).
    execution_generation: StrictPositiveInt
    content: str = Field(min_length=1, max_length=4000)
    locale: str = Field(default="en", min_length=2, max_length=16)
    request_id: str = Field(min_length=8, max_length=64)


class RunStatusResponse(BaseModel):
    """What Java may learn about a run without asking for model output."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    run_id: StrictPositiveInt
    state: RunState
    revision: StrictCounter
    updated_at: WireTimestamp
    error: RunError | None = None


class ErrorResponse(BaseModel):
    """The error envelope every internal endpoint returns."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    code: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=500)
    request_id: str | None = Field(default=None, max_length=64)
