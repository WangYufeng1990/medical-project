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

import json
from dataclasses import dataclass

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

        intents = list(dict.fromkeys([*answer.intents, *rules.intents]))
        if not intents:
            intents = [Intent.OTHER]
        risk = RiskLevel.highest(answer.risk, rules.risk)
        flags = list(rules.flags)
        requires_clinician = rules.requires_clinician
        if rules.risk is not answer.risk and answer.risk.rank < rules.risk.rank:
            flags.append("rule:raised_risk")
        if CLINICAL_DECISION in flags:
            risk = RiskLevel.highest(risk, RiskLevel.URGENT)
            requires_clinician = True
        return Classification(
            intents=intents,
            risk=risk,
            confidence=answer.confidence,
            flags=flags,
            requires_clinician=requires_clinician,
        )

    def _uncertain(self, rules) -> Classification:  # type: ignore[no-untyped-def]
        intents = list(rules.intents) or [Intent.OTHER]
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
        parsed = json.loads(_extract_json(response.text))
        if not isinstance(parsed, dict):
            raise ValueError("the classifier must answer with a JSON object")
        if set(parsed) - {"intents", "risk", "confidence"}:
            raise ValueError("unknown fields in the classifier's answer")
        confidence = parsed["confidence"]
        if isinstance(confidence, bool) or not isinstance(confidence, (int, float)):
            raise ValueError("confidence must be a number")
        if not 0.0 <= float(confidence) <= 1.0:
            raise ValueError("confidence must be between 0 and 1")
        intents = [Intent(value) for value in parsed["intents"]]
        if not intents:
            raise ValueError("at least one intent is required")
        return _ModelAnswer(
            intents=intents,
            risk=RiskLevel(parsed["risk"]),
            confidence=float(confidence),
        )


def _extract_json(text: str) -> str:
    """Tolerate a fenced block around the object; nothing more."""
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = stripped.split("\n", 1)[-1]
        stripped = stripped.rsplit("```", 1)[0]
    return stripped.strip()
