"""Service configuration (Round 55.1).

Everything the service needs to start comes from the environment. Startup fails
loudly when a credential the selected mode needs is missing, instead of accepting
requests and failing later: a service that silently runs without its inbound token
would answer calls it must refuse.
"""

from __future__ import annotations

from enum import StrEnum

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ProviderName(StrEnum):
    """The LLM providers this service can be configured with."""

    MOCK = "mock"
    DEEPSEEK = "deepseek"


class Settings(BaseSettings):
    """Environment-backed settings; the prefix keeps the service's namespace separate."""

    model_config = SettingsConfigDict(env_prefix="MEDICAL_AGENT_", extra="forbid")

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
        return self


def load_settings() -> Settings:
    """Read the environment once, at startup; raises when the configuration is unusable."""
    return Settings()
