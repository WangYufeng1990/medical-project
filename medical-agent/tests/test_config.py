"""Configuration rules (Round 55.1): a missing credential is a startup failure."""

from __future__ import annotations

import importlib
import sys

import pytest
from pydantic import ValidationError

from medical_agent.config import ConfigurationError, ProviderName, Settings, load_settings


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

    with pytest.raises(ValidationError, match="must be positive"):
        Settings()


def test_load_settings_reads_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")

    assert load_settings().service_name == "medical-agent"


def test_load_settings_fails_when_the_environment_is_incomplete(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # The startup path reports a ConfigurationError, not Pydantic's structure: its message
    # names the setting and carries no input.
    with pytest.raises(ConfigurationError, match="MEDICAL_AGENT_INBOUND_TOKEN"):
        load_settings()


def test_a_validation_error_does_not_echo_configured_credentials(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # A model-level failure renders the input by default; hide_input_in_errors keeps the
    # token and the API key out of the exception text.
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "synthetic-inbound-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "synthetic-api-key")
    monkeypatch.setenv("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS", "0")

    with pytest.raises(ValidationError) as caught:
        Settings()

    text = str(caught.value)
    assert "synthetic-inbound-token" not in text
    assert "synthetic-api-key" not in text


def test_the_startup_error_names_the_problem_without_the_credentials(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "synthetic-inbound-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "synthetic-api-key")
    monkeypatch.setenv("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS", "0")

    with pytest.raises(ConfigurationError) as caught:
        load_settings()

    text = str(caught.value)
    assert "synthetic-inbound-token" not in text
    assert "synthetic-api-key" not in text
    assert "MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS" in text, (
        "the error must still say which setting is wrong: " + text
    )
    assert caught.value.problems


def test_the_startup_entry_itself_refuses_and_stays_quiet_about_secrets(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # The module the ASGI server imports: load_settings() runs at import time, so a bad
    # environment must fail here — with text safe to print in a startup log.
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "synthetic-inbound-token")
    monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "synthetic-api-key")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", "deepseek")
    monkeypatch.setenv("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS", "0")
    sys.modules.pop("medical_agent.api.main", None)

    with pytest.raises(ConfigurationError) as caught:
        importlib.import_module("medical_agent.api.main")
    sys.modules.pop("medical_agent.api.main", None)

    text = str(caught.value)
    assert "synthetic-inbound-token" not in text
    assert "synthetic-api-key" not in text
