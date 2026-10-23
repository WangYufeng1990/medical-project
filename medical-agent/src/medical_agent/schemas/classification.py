"""The classification contract (Round 56.1).

Seven intents, one risk level and the flags a deterministic rule raised. Everything the
model returns is parsed into these types, so an answer that is not exactly this shape is a
failure rather than something to guess at.
"""

from enum import StrEnum
from typing import Annotated, Final, Literal

from pydantic import BaseModel, ConfigDict, Field


class Intent(StrEnum):
    APPOINTMENT = "APPOINTMENT"
    PRESCRIPTION_REFILL = "PRESCRIPTION_REFILL"
    BILLING = "BILLING"
    LAB_RESULTS = "LAB_RESULTS"
    CLINICAL_QUESTION = "CLINICAL_QUESTION"
    ADMINISTRATIVE_REQUEST = "ADMINISTRATIVE_REQUEST"
    OTHER = "OTHER"


class RiskLevel(StrEnum):
    ROUTINE = "ROUTINE"
    URGENT = "URGENT"
    EMERGENCY = "EMERGENCY"

    @property
    def rank(self) -> int:
        return _RANK[self]

    @classmethod
    def highest(cls, *levels: "RiskLevel") -> "RiskLevel":
        return max(levels, key=lambda level: level.rank)


_RANK: Final[dict["RiskLevel", int]] = {
    RiskLevel.ROUTINE: 0,
    RiskLevel.URGENT: 1,
    RiskLevel.EMERGENCY: 2,
}

CLASSIFICATION_VERSION: Final[Literal["v1"]] = "v1"


class Classification(BaseModel):
    """What the router decided, and why it may not be lowered."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    version: Literal["v1"] = CLASSIFICATION_VERSION
    intents: Annotated[list[Intent], Field(min_length=1, max_length=7)]
    risk: RiskLevel
    confidence: float = Field(ge=0.0, le=1.0)
    # Rule ids, e.g. "emergency:chest_pain"; they explain every escalation.
    flags: list[str] = Field(default_factory=list, max_length=16)
    # True when a clinician, not a template, has to answer.
    requires_clinician: bool = False
