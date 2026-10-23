"""The provider boundary (Round 55.1).

A provider turns a prompt into text. It never decides what happens to that text: the
caller validates it against a schema and, for this product, everything it produces is
a draft for staff review. Keeping the boundary this narrow is what lets the mock and
the real adapter be swapped without changing any business rule.
"""

from __future__ import annotations

from typing import Protocol

from pydantic import BaseModel, ConfigDict, Field


class ProviderRequest(BaseModel):
    """One completion request."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    prompt: str = Field(min_length=1, max_length=8000)
    max_output_tokens: int = Field(default=512, gt=0, le=4096)


class ProviderResponse(BaseModel):
    """One completion response, with the identity of what produced it."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    text: str
    provider: str
    model: str
    latency_ms: int = Field(ge=0)


class ProviderError(RuntimeError):
    """The provider failed in a way the caller must handle, never guess around."""


class Provider(Protocol):
    """An async text completion backend."""

    name: str

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        """Return a completion. Implementations must not retry past their own deadline."""
        ...

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        """The same call for callers that are already synchronous (the graph node)."""
        ...
