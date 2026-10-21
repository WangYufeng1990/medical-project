"""Provider selection from configuration (Round 55.1)."""

from __future__ import annotations

from medical_agent.config import ProviderName, Settings
from medical_agent.providers.base import Provider
from medical_agent.providers.mock import MockProvider


def build_provider(settings: Settings) -> Provider:
    """Return the provider the configuration asks for.

    DeepSeek is listed in the configuration but its adapter is Round 56.1. Asking for
    it before then must fail loudly rather than silently falling back to the mock: a
    deployment that believes it is calling the real model while it is not would make
    every evaluation meaningless.
    """
    match settings.provider:
        case ProviderName.MOCK:
            return MockProvider()
        case ProviderName.DEEPSEEK:
            raise NotImplementedError(
                "The DeepSeek provider is implemented in Round 56.1; set "
                "MEDICAL_AGENT_PROVIDER=mock until then"
            )
