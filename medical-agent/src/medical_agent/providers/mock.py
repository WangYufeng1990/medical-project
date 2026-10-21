"""A deterministic provider for tests and for running the service without a model.

It is deliberately boring: the same request always produces the same text, so a test
that fails is a test about our code, not about a model's mood. It does not pretend to
classify anything — round 56 adds the classification contract.
"""

from __future__ import annotations

from medical_agent.providers.base import ProviderRequest, ProviderResponse


class MockProvider:
    """Echoes the prompt length and a bounded prefix; no network, no randomness."""

    name = "mock"
    model = "mock-1"

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        preview = request.prompt.strip().splitlines()[0][:40] if request.prompt.strip() else ""
        return ProviderResponse(
            text=f"mock:{preview}",
            provider=self.name,
            model=self.model,
            latency_ms=0,
        )
