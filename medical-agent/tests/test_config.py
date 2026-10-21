"""Configuration rules (Round 55.1): a missing credential is a startup failure."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from medical_agent.config import ProviderName, Settings, load_settings


def test_inbound_token_is_required(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(ValidationError, match="MEDICAL_AGENT_INBOUND_TOKEN"):
        Settings()


def test_defaults_are_a_mock_service(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")

    settings = Settings()

    assert settings.provider is ProviderName.MOCK
    assert settings.service_name == "medical-agent"
    assert settings.request_timeout_seconds > 0


def test_the_token_is_not_echoed(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "super-secret-internal-token")

    settings = Settings()

    assert "super-secret-internal-token" not in repr(settings)
    assert settings.inbound_token is not None
    assert settings.inbound_token.get_secret_value() == "super-secret-internal-token"


def test_deepseek_without_a_key_fails(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")

    with pytest.raises(ValidationError, match="MEDICAL_AGENT_DEEPSEEK_API_KEY"):
        Settings()


def test_deepseek_with_a_key_is_a_valid_configuration(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "sk-test")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_MODEL", "deepseek-chat")

    settings = Settings()

    assert settings.provider is ProviderName.DEEPSEEK
    assert settings.deepseek_model == "deepseek-chat"


def test_an_unknown_provider_is_refused(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "gpt")

    with pytest.raises(ValidationError):
        Settings()


def test_an_unknown_setting_passed_in_code_is_refused(monkeypatch: pytest.MonkeyPatch) -> None:
    # Environment variables are only read for declared fields (an unknown variable is
    # simply not ours); a caller constructing Settings programmatically with a typo
    # must hear about it.
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")

    with pytest.raises(ValidationError):
        Settings(temperature=0.2)  # type: ignore[call-arg]


def test_a_non_positive_timeout_is_refused(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS", "0")

    with pytest.raises(ValidationError, match="timeout"):
        Settings()


def test_load_settings_reads_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")

    assert load_settings().service_name == "medical-agent"


def test_load_settings_fails_when_the_environment_is_incomplete(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    with pytest.raises(ValidationError):
        load_settings()
