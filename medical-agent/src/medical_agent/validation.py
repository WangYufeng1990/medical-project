"""Draft validation (Round 56.4).

The checks are about what must not leave the service: a citation that does not exist, a sentence
the evidence does not support, a chunk that contains instructions rather than policy, a draft that
is too long to review, and a clinical question answered as if it were administrative. The result is
a list of reasons, not a score: a reviewer reads the reasons, and any reason at all means the draft
is not usable as it stands.

"Supported" here means lexical overlap with the retrieved evidence, not entailment. That limit is
deliberate and stated: it catches a sentence that drifts off the policy, not a subtle paraphrase.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from medical_agent.drafting import CLINICAL_SUMMARY, NO_POLICY, SAFETY_NOTICE
from medical_agent.graph.state import RunState
from medical_agent.kb.retrieval import tokenize

MAX_DRAFT_CHARS = 1200
MIN_DRAFT_CHARS = 20
MIN_CLAIM_TOKENS = 4
SUPPORT_RATIO = 0.6

_MARKER = re.compile(r"\[chunk:([^\]]+)\]")
_SENTENCE = re.compile(r"[^.!?\n]+[.!?]?")
# The fixed sentences are routing and safety text, not claims about a policy.
_FIXED = frozenset(
    sentence.strip()
    for text in (SAFETY_NOTICE, CLINICAL_SUMMARY, NO_POLICY)
    for sentence in _SENTENCE.findall(text)
    if sentence.strip()
)

_INSTRUCTION = re.compile(
    r"ignore (all )?(previous|prior|above) instructions|disregard .*instruction|"
    r"you are now|system prompt|new instructions|reply only with|tell the patient to",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class Validation:
    ok: bool
    reasons: tuple[str, ...]


def contains_instructions(text: str) -> bool:
    """Retrieved text that tries to give the agent orders rather than a policy."""
    return bool(_INSTRUCTION.search(text))


def cited_chunk_ids(draft: str) -> tuple[str, ...]:
    return tuple(_MARKER.findall(draft))


def validate_draft(draft: str, state: RunState) -> Validation:
    reasons: list[str] = []
    evidence = state.get("evidence") or []
    citations = state.get("citations") or []
    known = {str(citation["chunk_id"]) for citation in citations}

    if len(draft) > MAX_DRAFT_CHARS:
        reasons.append("draft_too_long")
    if len(draft.strip()) < MIN_DRAFT_CHARS:
        reasons.append("draft_too_short")
    if SAFETY_NOTICE not in draft:
        reasons.append("missing_safety_notice")

    cited = cited_chunk_ids(draft)
    for chunk_id in cited:
        if chunk_id not in known:
            reasons.append(f"fabricated_citation:{chunk_id}")
    if evidence and not cited and not state.get("requires_clinician"):
        reasons.append("missing_citation")

    if any(contains_instructions(str(chunk["text"])) for chunk in evidence):
        reasons.append("untrusted_evidence")

    support = " ".join(str(chunk["text"]) for chunk in evidence)
    support_tokens = set(tokenize(support))
    for sentence in _claim_sentences(draft):
        tokens = set(tokenize(sentence))
        if len(tokens) < MIN_CLAIM_TOKENS:
            continue
        overlap = len(tokens & support_tokens) / len(tokens)
        if overlap < SUPPORT_RATIO:
            reasons.append(f"unsupported_claim:{sentence[:40]}")

    if state.get("requires_clinician"):
        if not draft.startswith(CLINICAL_SUMMARY):
            reasons.append("clinical_question_must_be_a_routing_summary")
        if cited:
            reasons.append("clinical_question_must_not_cite_policy")

    if contains_instructions(draft):
        reasons.append("instruction_in_draft")

    return Validation(ok=not reasons, reasons=tuple(reasons))


def _claim_sentences(draft: str) -> list[str]:
    sentences: list[str] = []
    for match in _SENTENCE.finditer(draft):
        sentence = match.group(0).strip()
        if not sentence or sentence in _FIXED or sentence.startswith("[chunk:"):
            continue
        sentences.append(sentence)
    return sentences
