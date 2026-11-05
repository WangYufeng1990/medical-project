"""The shapes a tool may accept from Java (Round 58.2).

The 58.1 DTO is the contract: identity, status, timing and display fields, nothing clinical. A 200
with a different shape is a broken contract, not a result, so it becomes `tool_failed` instead of
being handed to the drafting step.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from medical_agent.tools.registry import ToolDenied


class AppointmentItem(BaseModel):
    """Strict types and a real local time and zone (Round 58.2 review)."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    appointmentId: Annotated[int, Field(strict=True, gt=0)]
    status: Annotated[int, Field(strict=True, ge=0)]
    startTime: str
    timezone: str
    durationMinutes: Annotated[int, Field(strict=True, ge=0)] | None = None
    providerName: str | None = None
    department: str | None = None

    @field_validator("startTime")
    @classmethod
    def _local_time(cls, value: str) -> str:
        try:
            parsed = datetime.fromisoformat(value)
        except ValueError:
            raise ValueError("startTime is not an ISO-8601 local time") from None
        if parsed.tzinfo is not None:
            raise ValueError("startTime must be a wall time without an offset")
        return value

    @field_validator("timezone")
    @classmethod
    def _zone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except (ZoneInfoNotFoundError, ValueError):
            raise ValueError("timezone is not a known zone") from None
        return value


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
