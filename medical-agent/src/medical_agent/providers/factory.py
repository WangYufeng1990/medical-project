"""Provider selection from configuration (Round 55.1)."""

from __future__ import annotations

from medical_agent.config import ProviderName, Settings
from medical_agent.providers.base import Provider
from medical_agent.providers.deepseek import DeepSeekProvider
from medical_agent.providers.mock import MockProvider


def build_provider(settings: Settings) -> Provider:
    """Return the provider the configuration asks for.

    There is no silent fallback: a deployment that believes it is calling the real model
    while it is not would make every evaluation meaningless.
    """
    match settings.provider:
        case ProviderName.MOCK:
            return MockProvider()
        case ProviderName.DEEPSEEK:
            return DeepSeekProvider(settings)
