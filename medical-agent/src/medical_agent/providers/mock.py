"""A deterministic provider for tests and for running the service without a model.

It is deliberately boring: the same request always produces the same text, so a test
that fails is a test about our code, not about a model's mood. A prompt that asks for a
classification gets the classification contract's shape (Round 56.1), so a deployment
without a model still routes messages by the rules instead of escalating all of them;
any other prompt is echoed.
"""

from __future__ import annotations

import asyncio

from medical_agent.providers.base import ProviderRequest, ProviderResponse

CLASSIFICATION_ANSWER = '{"intents": ["OTHER"], "risk": "ROUTINE", "confidence": 0.5}'


class MockProvider:
    """Answers the classification prompt with its contract, echoes anything else."""

    name = "mock"
    model = "mock-1"

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        return asyncio.run(self.invoke(request))

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        preview = request.prompt.strip().splitlines()[0][:40] if request.prompt.strip() else ""
        text = CLASSIFICATION_ANSWER if "classif" in request.prompt.lower() else f"mock:{preview}"
        return ProviderResponse(
            text=text,
            provider=self.name,
            model=self.model,
            latency_ms=0,
        )
