"""Health endpoints (Round 55.1): liveness is the process, readiness is the provider."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from medical_agent.api.app import create_app
from medical_agent.config import Settings


def settings_for(monkeypatch: pytest.MonkeyPatch, provider: str = "mock") -> Settings:
    monkeypatch.setenv("MEDICAL_AGENT_INBOUND_TOKEN", "internal-token")
    monkeypatch.setenv("MEDICAL_AGENT_OUTBOUND_TOKEN", "outbound-token")
    monkeypatch.setenv("MEDICAL_AGENT_PROVIDER", provider)
    if provider == "deepseek":
        monkeypatch.setenv("MEDICAL_AGENT_DEEPSEEK_API_KEY", "sk-test")
    return Settings()


def test_liveness_does_not_depend_on_the_provider(monkeypatch: pytest.MonkeyPatch) -> None:
    # Even a service whose provider cannot be built is alive: the process answers.
    client = TestClient(create_app(settings_for(monkeypatch)))

    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "medical-agent"}


def test_readiness_reports_the_provider_and_the_contract(monkeypatch: pytest.MonkeyPatch) -> None:
    client = TestClient(create_app(settings_for(monkeypatch)))

    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "service": "medical-agent",
        "provider": "mock",
        "contract_version": "v1",
    }


def test_readiness_reports_the_deepseek_adapter(monkeypatch: pytest.MonkeyPatch) -> None:
    client = TestClient(create_app(settings_for(monkeypatch, provider="deepseek")))

    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json()["provider"] == "deepseek"


def test_readiness_answers_503_when_the_provider_cannot_be_built(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Readiness must not claim a broken provider is ready: the Java side keeps working, and
    # the failure is visible to whoever asks.
    import medical_agent.api.app as module

    def boom(settings: Settings) -> object:
        raise RuntimeError("no credential")

    monkeypatch.setattr(module, "build_provider", boom)
    client = TestClient(create_app(settings_for(monkeypatch)))

    response = client.get("/health/ready")

    assert response.status_code == 503
    assert "cannot be built" in response.json()["detail"]
