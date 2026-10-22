"""The provider boundary (Round 55.1)."""

from __future__ import annotations

import asyncio

import pytest
from pydantic import ValidationError

from medical_agent.config import ProviderName, Settings
from medical_agent.providers.base import ProviderRequest
from medical_agent.providers.factory import build_provider
from medical_agent.providers.mock import MockProvider


def test_the_mock_is_deterministic() -> None:
    provider = MockProvider()
    request = ProviderRequest(prompt="Patient asks about a refill")

    first = asyncio.run(provider.invoke(request))
    second = asyncio.run(provider.invoke(request))

    assert first == second
    assert first.provider == "mock"
    assert first.text == "mock:Patient asks about a refill"


def test_the_mock_reports_its_own_cost_of_zero() -> None:
    response = asyncio.run(MockProvider().invoke(ProviderRequest(prompt="x")))

    assert response.latency_ms == 0


def test_a_request_must_carry_a_prompt() -> None:
    with pytest.raises(ValidationError):
        ProviderRequest(prompt="")


def test_a_request_cannot_ask_for_unbounded_output() -> None:
    with pytest.raises(ValidationError):
        ProviderRequest(prompt="x", max_output_tokens=100_000)


def test_the_factory_returns_the_mock(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_OUTBOUND_TOKEN", "outbound-token")

    provider = build_provider(Settings())

    assert isinstance(provider, MockProvider)
    assert provider.name == "mock"


def test_the_factory_refuses_deepseek_until_round_56(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_OUTBOUND_TOKEN", "outbound-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "sk-test")

    with pytest.raises(NotImplementedError, match=r"56\.1"):
        build_provider(Settings())


def test_the_provider_enum_names_what_is_configured() -> None:
    assert {provider.value for provider in ProviderName} == {"mock", "deepseek"}
