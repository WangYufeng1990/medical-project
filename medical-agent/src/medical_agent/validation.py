"""Draft validation (Round 56.4).

The checks are about what must not leave the service: a citation that does not exist, a sentence
its cited chunk does not support, a chunk that contains instructions rather than policy, a draft
that is too long to review, and a clinical question answered as if it were administrative.

"What may appear" depends on the draft's kind. A policy answer must cite the chunk behind each
claim; a routing summary and the safe fallback must cite nothing, because they make no claim about
a policy. The support check binds every sentence to the chunk that sentence cites, not to the whole
retrieved set.

Nothing here is entailment: "supported" means the sentence's content words overlap the cited
chunk, or, for a short sentence, that the chunk contains it verbatim. That limit is deliberate.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from medical_agent.drafting import (
    CLINICAL_SUMMARY,
    NO_POLICY,
    POLICY_ANSWER,
    SAFETY_NOTICE,
)
from medical_agent.graph.state import RunState
from medical_agent.kb.retrieval import tokenize

MAX_DRAFT_CHARS = 1200
MIN_DRAFT_CHARS = 20
MIN_CLAIM_TOKENS = 4
SUPPORT_RATIO = 0.6

_MARKER = re.compile(r"\[chunk:([^\]]+)\]")
_SENTENCE = re.compile(r"[^.!?\n]+[.!?]?")
_INSTRUCTION = re.compile(
    r"ignore (all )?(previous|prior|above) instructions|disregard .*instruction|"
    r"you are now|system prompt|new instructions|reply only with|tell the patient to",
    re.IGNORECASE,
)

# The fixed sentences are routing and safety text, not claims about a policy.
_FIXED = frozenset(
    sentence.strip()
    for text in (SAFETY_NOTICE, CLINICAL_SUMMARY, NO_POLICY)
    for sentence in _SENTENCE.findall(text)
    if sentence.strip()
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


def validate_draft(draft: str, state: RunState, require_citation: bool) -> Validation:
    reasons: list[str] = []
    evidence = state.get("evidence") or []
    citations = state.get("citations") or []
    known = {str(citation["chunk_id"]) for citation in citations}
    texts = {str(chunk["chunk_id"]): str(chunk["text"]) for chunk in evidence}

    if len(draft) > MAX_DRAFT_CHARS:
        reasons.append("draft_too_long")
    if len(draft.strip()) < MIN_DRAFT_CHARS:
        reasons.append("draft_too_short")
    if SAFETY_NOTICE not in draft:
        reasons.append("missing_safety_notice")
    if any(contains_instructions(str(chunk["text"])) for chunk in evidence):
        reasons.append("untrusted_evidence")

    for sentence in _claims(draft):
        identifiers = _MARKER.findall(sentence)
        for chunk_id in identifiers:
            if chunk_id not in known:
                reasons.append(f"fabricated_citation:{chunk_id}")
            elif chunk_id not in texts:
                reasons.append(f"citation_without_evidence:{chunk_id}")
        if require_citation:
            if not identifiers:
                reasons.append(f"missing_citation:{sentence[:40]}")
                continue
            _check_support(sentence, identifiers, texts, reasons)
        elif identifiers:
            reasons.append("non_policy_draft_must_not_cite")
        else:
            # A routing or fallback draft may only contain its fixed sentences: anything else is
            # a claim with no policy behind it.
            reasons.append(f"unexpected_claim:{sentence[:40]}")

    if state.get("requires_clinician") and not draft.startswith(CLINICAL_SUMMARY):
        reasons.append("clinical_question_must_be_a_routing_summary")
    if contains_instructions(draft):
        reasons.append("instruction_in_draft")

    return Validation(ok=not reasons, reasons=tuple(reasons))


def _check_support(
    sentence: str, identifiers: list[str], texts: dict[str, str], reasons: list[str]
) -> None:
    cited = " ".join(texts.get(chunk_id, "") for chunk_id in identifiers)
    claim = _MARKER.sub(" ", sentence).strip()
    tokens = set(tokenize(claim))
    if len(tokens) < MIN_CLAIM_TOKENS:
        # A short claim is not exempt: it must appear in the chunk it cites.
        normalized_claim = " ".join(claim.lower().split()).strip(" .;:")
        normalized_cited = " ".join(cited.lower().split())
        if normalized_claim not in normalized_cited:
            reasons.append(f"unsupported_claim:{claim[:40]}")
        return
    cited_tokens = set(tokenize(cited))
    overlap = len(tokens & cited_tokens) / len(tokens)
    if overlap < SUPPORT_RATIO:
        reasons.append(f"unsupported_claim:{claim[:40]}")


def _claims(draft: str) -> list[str]:
    claims: list[str] = []
    for match in _SENTENCE.finditer(draft):
        sentence = match.group(0).strip()
        if not sentence or sentence in _FIXED or sentence.startswith("Prepared for staff"):
            continue
        claims.append(sentence)
    return claims


def adopted_citations(state: RunState) -> list[dict[str, object]]:
    """The citations the draft actually relies on: none for routing or fallback text."""
    if state.get("kind") != POLICY_ANSWER:
        return []
    used = {chunk_id for chunk_id in cited_chunk_ids(str(state.get("draft", "")))}
    return [
        citation for citation in state.get("citations") or []
        if str(citation["chunk_id"]) in used
    ]
