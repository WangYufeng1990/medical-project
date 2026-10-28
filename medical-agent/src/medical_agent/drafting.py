"""Draft construction (Round 56.4).

An administrative answer is built from the retrieved evidence and names the chunk it came from,
so the validator and the reviewer can trace every sentence. A clinical question gets a routing
summary instead of an answer: the agent is not allowed to give advice, and the only thing it may
say is that a clinician will respond.
"""

from __future__ import annotations

from medical_agent.graph.state import RunState

SAFETY_NOTICE = "Prepared for staff review; not medical advice."
CLINICAL_SUMMARY = (
    "A member of the care team will review your message and respond. "
    "If this is an emergency, call 911."
)
NO_POLICY = (
    "I could not find a practice policy that answers this. "
    "A member of the care team will review your message and respond."
)
CITATION_MARKER = "[chunk:{chunk_id}]"


def citation_marker(chunk_id: str) -> str:
    return CITATION_MARKER.format(chunk_id=chunk_id)


def build_draft(state: RunState) -> str:
    if state.get("requires_clinician"):
        return f"{CLINICAL_SUMMARY}\n\n{SAFETY_NOTICE}"
    evidence = state.get("evidence") or []
    if not evidence:
        return f"{NO_POLICY}\n\n{SAFETY_NOTICE}"
    top = evidence[0]
    support = _first_sentence(str(top["text"]))
    return (
        f"According to the practice policy: {support} "
        f"{citation_marker(str(top['chunk_id']))}\n\n{SAFETY_NOTICE}"
    )


def safe_fallback(state: RunState) -> str:
    """What to send when the built draft does not pass validation: no claims, no citations."""
    return (
        "A member of the care team will review your message and respond. "
        f"If this is an emergency, call 911.\n\n{SAFETY_NOTICE}"
    )


def _first_sentence(text: str) -> str:
    body = text.split("\n", 1)[-1].strip()
    for separator in (". ", "? ", "! "):
        cut = body.find(separator)
        if cut != -1:
            return body[: cut + 1].strip()
    return body.strip()
