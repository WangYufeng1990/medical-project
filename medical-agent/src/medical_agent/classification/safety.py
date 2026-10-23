"""Deterministic safety rules (Round 56.1).

The model proposes; these rules can only raise. That direction is the whole design: a model
that says ROUTINE about chest pain cannot lower it, and a model that fails outright still
leaves the rules standing.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from medical_agent.schemas.classification import Intent, RiskLevel


@dataclass(frozen=True)
class SafetyRule:
    id: str
    risk: RiskLevel
    intent: Intent | None
    pattern: re.Pattern[str]
    requires_clinician: bool = False


def _rule(rule_id: str, risk: RiskLevel, intent: Intent | None, pattern: str,
          requires_clinician: bool = True) -> SafetyRule:
    return SafetyRule(rule_id, risk, intent, re.compile(pattern, re.IGNORECASE),
                      requires_clinician)


# Typographic characters and line breaks are ordinary ways to type the same sentence, so the
# rules see one normalised copy of the text. The message itself is kept verbatim for the
# model and the draft.
_QUOTES = str.maketrans({"\u2019": "'", "\u2018": "'", "\u201c": '"', "\u201d": '"',
                         "\u00a0": " ", "\u2013": "-", "\u2014": "-"})
_WHITESPACE = re.compile(r"\s+")


def normalize_for_rules(text: str) -> str:
    return _WHITESPACE.sub(" ", text.translate(_QUOTES)).strip()


RULES: tuple[SafetyRule, ...] = (
    _rule("emergency:chest_pain", RiskLevel.EMERGENCY, None,
          r"\bchest pain\b|\bcrushing chest\b|\bpressure in my chest\b"),
    _rule("emergency:breathing", RiskLevel.EMERGENCY, None,
          r"can'?t breathe|cannot breathe|shortness of breath|gasping"),
    _rule("emergency:stroke", RiskLevel.EMERGENCY, None,
          r"face droop|slurred speech|sudden weakness|numb on one side"),
    _rule("emergency:bleeding", RiskLevel.EMERGENCY, None,
          r"severe bleeding|bleeding heavily|won'?t stop bleeding"),
    _rule("emergency:self_harm", RiskLevel.EMERGENCY, None,
          r"suicidal|kill myself|end my life|overdose|took too many pills"),
    _rule("emergency:seizure", RiskLevel.EMERGENCY, None,
          r"\bseizure\b|\bunconscious\b|\bpassed out\b"),
    _rule("urgent:infant_fever", RiskLevel.URGENT, None,
          r"newborn.{0,20}fever|infant.{0,20}fever|baby.{0,20}fever"),
    _rule("urgent:pregnancy_bleeding", RiskLevel.URGENT, None,
          r"pregnan\w*.{0,30}bleed|bleed\w*.{0,30}pregnan"),
    _rule("clinical_decision", RiskLevel.URGENT, Intent.CLINICAL_QUESTION,
          r"should i stop taking|should i take|what dose|how much should i take|"
          r"is it safe to take|can i skip"),
    _rule("appointment", RiskLevel.ROUTINE, Intent.APPOINTMENT,
          r"\bappointment\b|\breschedul\w+|\bbook\b|\bschedule\b|\bvisit\b",
          requires_clinician=False),
    _rule("refill", RiskLevel.ROUTINE, Intent.PRESCRIPTION_REFILL,
          r"\brefill\b|\bprescription\b|\bpharmacy\b", requires_clinician=False),
    _rule("billing", RiskLevel.ROUTINE, Intent.BILLING,
          r"\bbill\b|\binvoice\b|\binsurance\b|\bcopay\b|\bpayment\b",
          requires_clinician=False),
    _rule("lab_results", RiskLevel.ROUTINE, Intent.LAB_RESULTS,
          r"\blab results?\b|\bblood test\b|\bx-?ray\b|\bmri\b", requires_clinician=False),
)

CLINICAL_DECISION = "clinical_decision"


@dataclass(frozen=True)
class RuleOutcome:
    risk: RiskLevel
    intents: tuple[Intent, ...]
    flags: tuple[str, ...]
    requires_clinician: bool


def apply_rules(text: str) -> RuleOutcome:
    normalised = normalize_for_rules(text)
    risk = RiskLevel.ROUTINE
    intents: list[Intent] = []
    flags: list[str] = []
    requires_clinician = False
    for rule in RULES:
        if not rule.pattern.search(normalised):
            continue
        risk = RiskLevel.highest(risk, rule.risk)
        flags.append(rule.id)
        if rule.intent is not None and rule.intent not in intents:
            intents.append(rule.intent)
        requires_clinician = requires_clinician or rule.requires_clinician
    return RuleOutcome(risk, tuple(intents), tuple(flags), requires_clinician)
