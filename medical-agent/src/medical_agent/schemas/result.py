"""The result contract (Round 57.1).

What Java needs in order to show a draft for review: the routing decision, the draft itself,
the citations it relies on and the validator's verdict. It is a separate schema from the start
request so the two can evolve independently.
"""

from datetime import datetime
from typing import Annotated, Final, Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from medical_agent.schemas.classification import Intent, RiskLevel
from medical_agent.schemas.run import StrictPositiveInt

CONTRACT_VERSION: Final[Literal["v1"]] = "v1"


class CitationPayload(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    chunk_id: Annotated[str, Field(min_length=3, max_length=80,
                                   pattern=r"^[A-Za-z0-9_.:-]+$")]
    document_id: Annotated[str, Field(min_length=1, max_length=80,
                                      pattern=r"^[A-Za-z0-9_-]+$")]
    document_version: StrictPositiveInt
    title: Annotated[str, Field(min_length=1, max_length=200)]
    heading_path: Annotated[list[str], Field(max_length=8)]
    content_hash: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]


class ValidationPayload(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    ok: bool
    kind: Annotated[str, Field(min_length=1, max_length=32)]
    reasons: Annotated[list[str], Field(max_length=8)]
    rejected_reasons: Annotated[list[str], Field(max_length=8)]


class ToolFact(BaseModel):
    """One appointment as the tool returned it (Round 58.2): the 58.1 fields, nothing clinical."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    appointment_id: StrictPositiveInt
    status: Annotated[int, Field(ge=0)]
    start_time: Annotated[str, Field(min_length=10, max_length=40)]
    timezone: Annotated[str, Field(min_length=1, max_length=64)]
    duration_minutes: Annotated[int, Field(ge=0)] | None = None
    provider_name: Annotated[str, Field(max_length=200)] | None = None
    department: Annotated[str, Field(max_length=120)] | None = None

    @field_validator("start_time")
    @classmethod
    def _local_time(cls, value: str) -> str:
        try:
            parsed = datetime.fromisoformat(value)
        except ValueError:
            raise ValueError("start_time is not an ISO-8601 local time") from None
        if parsed.tzinfo is not None:
            raise ValueError("start_time must be a wall time without an offset")
        return value

    @field_validator("timezone")
    @classmethod
    def _zone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except (ZoneInfoNotFoundError, ValueError):
            raise ValueError("timezone is not a known zone") from None
        return value


class ToolPayload(BaseModel):
    """What the run's read-only tool did, for the review context (Round 58.2)."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    name: Annotated[str, Field(min_length=1, max_length=40)]
    status: Literal["ok", "needs_human"]
    # A fixed failure code, never free text: the reason reaches the review result, where model or
    # transport text must not appear (Round 58.2 review).
    reason: Literal["tool_failed", "deadline_exceeded", "call_budget_exhausted",
                    "tool_unavailable"] | None = None
    facts: Annotated[list[ToolFact], Field(max_length=5)]

    @model_validator(mode="after")
    def _consistent(self) -> "ToolPayload":
        # A human handover is a fixed code with no facts; a successful read has no reason (facts may
        # legitimately be empty: the query worked and there is nothing upcoming).
        if self.status == "needs_human":
            if self.facts:
                raise ValueError("needs_human carries no facts")
            if not self.reason:
                raise ValueError("needs_human needs a reason")
        elif self.reason is not None:
            raise ValueError("a successful tool result has no reason")
        return self


class RunResultRequest(BaseModel):
    """The result a worker submits for one generation of one run."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    run_id: StrictPositiveInt
    generation: StrictPositiveInt
    intent: Intent
    risk: RiskLevel
    requires_clinician: bool
    draft: Annotated[str, Field(min_length=1, max_length=1500)]
    citations: Annotated[list[CitationPayload], Field(max_length=8)]
    validation: ValidationPayload
    # Absent for a run that used no tool; present with status ok (facts may be empty) or
    # needs_human with a fixed reason and no facts.
    tool: ToolPayload | None = None
