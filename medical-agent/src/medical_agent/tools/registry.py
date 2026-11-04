"""The tool surface the agent may call (Round 58.2).

A model chooses among named tools, so the name and the arguments are the untrusted part of the
protocol: unknown names are refused, arguments are validated against a strict model that forbids
extra fields, and nothing is sent anywhere until validation passes. The patient is never an argument
— it comes from the run the tool executes for.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Annotated, Final

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from medical_agent.schemas.run import StrictPositiveInt

MAX_CALLS_PER_RUN: Final[int] = 5
DEFAULT_DEADLINE_SECONDS: Final[float] = 10.0


class ToolDenied(RuntimeError):
    """The tool call was refused before it could execute."""

    def __init__(self, code: str, detail: str = "") -> None:
        super().__init__(code if not detail else f"{code}: {detail}")
        self.code = code


class AppointmentsArguments(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    limit: Annotated[int, Field(ge=1, le=20)] = 10


class AppointmentArguments(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    appointment_id: StrictPositiveInt


@dataclass(frozen=True)
class Tool:
    name: str
    arguments: type[BaseModel]
    path: Callable[[int, BaseModel], str]
    description: str


def _appointments_path(run_id: int, args: BaseModel) -> str:
    return f"/api/v1/internal/agent/runs/{run_id}/appointments?limit={getattr(args, 'limit', 10)}"


def _appointment_path(run_id: int, args: BaseModel) -> str:
    return (f"/api/v1/internal/agent/runs/{run_id}/appointments/"
            f"{args.appointment_id}")


TOOLS: Final[dict[str, Tool]] = {
    "get_my_appointments": Tool(
        "get_my_appointments", AppointmentsArguments, _appointments_path,
        "Upcoming appointments of the patient this run serves."),
    "get_appointment": Tool(
        "get_appointment", AppointmentArguments, _appointment_path,
        "One appointment of the patient this run serves, by id."),
}


@dataclass
class ToolResult:
    name: str
    payload: object | None = None
    needs_human: bool = False
    reason: str | None = None


@dataclass
class ToolExecutor:
    """Executes at most MAX_CALLS_PER_RUN calls within a deadline."""

    client: Callable[[str], object]
    max_calls: int = MAX_CALLS_PER_RUN
    calls: int = field(default=0, init=False)

    def execute(self, name: str, arguments: dict[str, object], run_id: int) -> ToolResult:
        tool = TOOLS.get(name)
        if tool is None:
            raise ToolDenied("unknown_tool", name)
        try:
            parsed = tool.arguments.model_validate(arguments)
        except ValidationError as invalid:
            raise ToolDenied("invalid_arguments", str(invalid)) from invalid
        if self.calls >= self.max_calls:
            raise ToolDenied("call_budget_exhausted", str(self.max_calls))
        self.calls += 1
        # The model's text in the result is data: it cannot grant another patient, another run or
        # another tool, because the path is built from the run and the validated arguments only.
        return ToolResult(name=name, payload=self.client(tool.path(run_id, parsed)))
