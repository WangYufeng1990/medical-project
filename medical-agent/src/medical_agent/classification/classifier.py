"""Classification: ask the model, then let the rules decide the floor (Round 56.1).

The model's answer is parsed strictly and merged with the deterministic rules. Three
outcomes are possible and all of them end in a classification:

* a valid answer — model intents/risk, raised to the rules' floor where the rules are stricter;
* an invalid answer or a provider failure — rules only, plus ``uncertain_output`` and at
  least URGENT, because "we could not tell" must not read as routine;
* any answer that asks for a clinical decision — flagged, clinician-required, at least URGENT.

Nothing here publishes anything: every classification is a routing decision for staff.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field

from medical_agent.classification.safety import CLINICAL_DECISION, apply_rules
from medical_agent.providers.base import Provider, ProviderRequest
from medical_agent.schemas.classification import Classification, Intent, RiskLevel

UNCERTAIN_OUTPUT = "uncertain_output"
UNCERTAIN_MINIMUM = RiskLevel.URGENT

PROMPT_TEMPLATE = """You classify a patient's portal message for a medical practice.
Answer with one JSON object and nothing else:
{{"intents": [..], "risk": "ROUTINE|URGENT|EMERGENCY", "confidence": 0.0-1.0}}
Allowed intents: {intents}.
Never lower the risk of a message that describes an emergency.

Message:
{content}
"""


@dataclass(frozen=True)
class _ModelAnswer:
    intents: list[Intent]
    risk: RiskLevel
    confidence: float


class _Envelope(BaseModel):
    """The raw answer, validated before anything is merged (Round 56.1 review).

    Strict on purpose: an intents value that is not a JSON array of strings, a confidence that
    is not a number in 0-1, or an unknown field is a failure, not something to coerce.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    intents: Annotated[list[str], Field(min_length=1, max_length=7)]
    risk: str
    confidence: Annotated[float, Field(ge=0.0, le=1.0)]


MAX_ANSWER_CHARS = 4096


class Classifier:
    def __init__(self, provider: Provider, max_prompt_chars: int = 8000) -> None:
        self._provider = provider
        self._max_prompt_chars = max_prompt_chars

    def classify(self, content: str) -> Classification:
        rules = apply_rules(content)
        try:
            answer = self._ask(content)
        except Exception:  # an unreadable answer must never read as routine
            return self._uncertain(rules)

        # A specific intent is the answer; OTHER only survives when nothing else was found, so
        # the primary intent (the first one) is never OTHER because the model said so.
        merged = dict.fromkeys([*answer.intents, *rules.intents])
        specific: list[Intent] = [intent for intent in merged if intent is not Intent.OTHER]
        intents: list[Intent] = specific if specific else [Intent.OTHER]
        risk = RiskLevel.highest(answer.risk, rules.risk)
        flags = list(rules.flags)
        requires_clinician = rules.requires_clinician
        if rules.risk is not answer.risk and answer.risk.rank < rules.risk.rank:
            flags.append("rule:raised_risk")
        if CLINICAL_DECISION in flags:
            risk = RiskLevel.highest(risk, RiskLevel.URGENT)
            requires_clinician = True
        # The final classification decides these, so a legal answer cannot route a clinical
        # question or an emergency as "no clinician needed" (Round 56.1 review).
        if Intent.CLINICAL_QUESTION in intents:
            risk = RiskLevel.highest(risk, RiskLevel.URGENT)
            requires_clinician = True
        if risk is RiskLevel.EMERGENCY:
            requires_clinician = True
        return Classification(
            intents=intents,
            risk=risk,
            confidence=answer.confidence,
            flags=flags,
            requires_clinician=requires_clinician,
        )

    def _uncertain(self, rules) -> Classification:  # type: ignore[no-untyped-def]
        specific: list[Intent] = [intent for intent in rules.intents if intent is not Intent.OTHER]
        intents: list[Intent] = specific if specific else [Intent.OTHER]
        return Classification(
            intents=intents,
            risk=RiskLevel.highest(rules.risk, UNCERTAIN_MINIMUM),
            confidence=0.0,
            flags=[*rules.flags, UNCERTAIN_OUTPUT],
            requires_clinician=True,
        )

    def _ask(self, content: str) -> _ModelAnswer:
        prompt = PROMPT_TEMPLATE.format(
            intents=", ".join(intent.value for intent in Intent),
            content=content[: self._max_prompt_chars],
        )
        response = self._provider.invoke_now(ProviderRequest(prompt=prompt))
        if len(response.text) > MAX_ANSWER_CHARS:
            raise ValueError("the classifier's answer is too long to be the contract")
        envelope = _Envelope.model_validate_json(_extract_json(response.text))
        try:
            intents: list[Intent] = [Intent(value) for value in envelope.intents]
            risk = RiskLevel(envelope.risk)
        except ValueError:
            raise ValueError("unknown intent or risk in the classifier's answer") from None
        return _ModelAnswer(intents=intents, risk=risk, confidence=envelope.confidence)


def _extract_json(text: str) -> str:
    """Tolerate a fenced block around the object; nothing more."""
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = stripped.split("\n", 1)[-1]
        stripped = stripped.rsplit("```", 1)[0]
    return stripped.strip()
