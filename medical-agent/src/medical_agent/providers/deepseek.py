"""The DeepSeek adapter (Round 56.1).

The only place that talks to a real model. Failures are reduced to ``ProviderError`` with a
status code or a fixed description — the response body is never carried, because it can quote
the patient's message.
"""

from __future__ import annotations

import asyncio
from time import perf_counter

import httpx

from medical_agent.config import Settings
from medical_agent.providers.base import ProviderError, ProviderRequest, ProviderResponse


class DeepSeekProvider:
    name = "deepseek"

    def __init__(self, settings: Settings) -> None:
        if settings.deepseek_api_key is None:
            raise ProviderError("deepseek requires an API key")
        self._key = settings.deepseek_api_key.get_secret_value()
        self._base_url = settings.deepseek_base_url.rstrip("/")
        self._model = settings.deepseek_model
        self._timeout = settings.request_timeout_seconds

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        return asyncio.run(self.invoke(request))

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        started = perf_counter()
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(
                    f"{self._base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self._key}"},
                    json={
                        "model": self._model,
                        "messages": [{"role": "user", "content": request.prompt}],
                        "max_tokens": request.max_output_tokens,
                        "temperature": 0,
                    },
                )
        except httpx.TimeoutException:
            raise ProviderError("deepseek timed out") from None
        except httpx.HTTPError:
            raise ProviderError("deepseek could not be reached") from None

        if response.status_code != 200:
            raise ProviderError(f"deepseek answered {response.status_code}")
        try:
            text = response.json()["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError):
            raise ProviderError("deepseek answered with an unexpected shape") from None
        if not isinstance(text, str) or not text.strip():
            raise ProviderError("deepseek answered with no text")
        prompt_tokens, completion_tokens = _usage_tokens(response.json().get("usage"))
        return ProviderResponse(
            text=text,
            provider=self.name,
            model=self._model,
            latency_ms=int((perf_counter() - started) * 1000),
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
        )


def _usage_tokens(usage: object) -> tuple[int | None, int | None]:
    """Usage is optional statistics: a malformed block means "not measured", not a failed answer.

    The answer itself is kept, the tokens become None, and nothing from the response is logged
    (Round 59.2 review).
    """
    if not isinstance(usage, dict):
        return None, None
    prompt = usage.get("prompt_tokens")
    completion = usage.get("completion_tokens")
    if (isinstance(prompt, bool) or isinstance(completion, bool)
            or not isinstance(prompt, int) or not isinstance(completion, int)
            or prompt < 0 or completion < 0):
        return None, None
    return prompt, completion
