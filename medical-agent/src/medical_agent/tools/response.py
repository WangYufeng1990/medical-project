"""The shapes a tool may accept from Java (Round 58.2).

The 58.1 DTO is the contract: identity, status, timing and display fields, nothing clinical. A 200
with a different shape is a broken contract, not a result, so it becomes `tool_failed` instead of
being handed to the drafting step.
"""

from __future__ import annotations

from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from medical_agent.tools.registry import ToolDenied


class AppointmentItem(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    appointmentId: Annotated[int, Field(gt=0)]
    status: int
    startTime: str
    timezone: str
    durationMinutes: int | None = None
    providerName: str | None = None
    department: str | None = None


class AppointmentList(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    items: list[AppointmentItem]


def parse_list(envelope: object) -> list[AppointmentItem]:
    if not isinstance(envelope, list):
        raise ToolDenied("tool_failed", "unexpected list envelope")
    try:
        return [AppointmentItem.model_validate(item) for item in envelope]
    except ValidationError:
        raise ToolDenied("tool_failed", "unexpected appointment shape") from None


def parse_one(envelope: object) -> AppointmentItem:
    try:
        return AppointmentItem.model_validate(envelope)
    except ValidationError:
        raise ToolDenied("tool_failed", "unexpected appointment shape") from None
