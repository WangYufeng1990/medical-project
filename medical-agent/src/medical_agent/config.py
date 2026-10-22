"""Service configuration (Round 55.1).

Everything the service needs to start comes from the environment. Startup fails
loudly when a credential the selected mode needs is missing, instead of accepting
requests and failing later: a service that silently runs without its inbound token
would answer calls it must refuse.
"""

from __future__ import annotations

from collections.abc import Sequence
from enum import StrEnum

from pydantic import SecretStr, ValidationError, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ProviderName(StrEnum):
    """The LLM providers this service can be configured with."""

    MOCK = "mock"
    DEEPSEEK = "deepseek"


class Settings(BaseSettings):
    """Environment-backed settings; the prefix keeps the service's namespace separate."""

    # hide_input_in_errors keeps configured values out of the exception text: a
    # model-level failure otherwise renders the whole input, and a short token would
    # appear verbatim (Round 55.1 review).
    model_config = SettingsConfigDict(
        env_prefix="MEDICAL_AGENT_", extra="forbid", hide_input_in_errors=True
    )

    service_name: str = "medical-agent"

    # Token the Java business layer presents on every internal call. Required: an
    # agent that accepts unauthenticated work is not deployable.
    inbound_token: SecretStr | None = None

    provider: ProviderName = ProviderName.MOCK
    deepseek_api_key: SecretStr | None = None
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    request_timeout_seconds: float = 10.0
    max_prompt_chars: int = 8000

    # The worker calls Java back (Round 55.4). Required when a run is dispatched: without
    # them the worker could only fail after doing the model work.
    java_base_url: str = "http://localhost:8080"
    outbound_token: SecretStr | None = None

    # Postgres in deployment, SQLite for a developer machine and the tests.
    checkpoint_url: str = "sqlite://./.agent/checkpoints.sqlite"

    @model_validator(mode="after")
    def check_credentials(self) -> Settings:
        if self.inbound_token is None or not self.inbound_token.get_secret_value():
            raise ValueError(
                "MEDICAL_AGENT_INBOUND_TOKEN is required: internal calls are authenticated"
            )
        if self.provider is ProviderName.DEEPSEEK and self.deepseek_api_key is None:
            raise ValueError("MEDICAL_AGENT_DEEPSEEK_API_KEY is required when provider=deepseek")
        if self.request_timeout_seconds <= 0:
            raise ValueError("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS must be positive")
        if self.outbound_token is None or not self.outbound_token.get_secret_value():
            raise ValueError(
                "MEDICAL_AGENT_OUTBOUND_TOKEN is required: results are submitted to Java"
            )
        return self


class ConfigurationError(RuntimeError):
    """A startup failure whose text names the problem and never echoes a credential.

    Pydantic keeps the raw input in ``ValidationError.errors()`` even when its message
    hides it, so the startup path does not hand that structure to a logger: only the
    field path and the message survive.
    """

    def __init__(self, problems: Sequence[str]) -> None:
        self.problems = tuple(problems)
        super().__init__("Invalid agent configuration: " + "; ".join(self.problems))


def _problems(exc: ValidationError) -> list[str]:
    problems: list[str] = []
    for error in exc.errors():
        location = ".".join(str(part) for part in error.get("loc", ())) or "settings"
        problems.append(f"{location}: {error.get('msg', 'invalid')}")
    return problems


def load_settings() -> Settings:
    """Read the environment once, at startup; raises a sanitized error when unusable."""
    try:
        return Settings()
    except ValidationError as exc:
        # `from None`: the original error keeps the raw input in its own structure and
        # there is no reason to attach it to what a logger will print.
        raise ConfigurationError(_problems(exc)) from None
