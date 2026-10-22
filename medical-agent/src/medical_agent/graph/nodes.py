"""The minimal graph's nodes (Round 55.4): validate, classify, draft, submit."""

import asyncio
from collections.abc import Callable

from medical_agent.graph.state import RunState
from medical_agent.providers.base import Provider, ProviderRequest

CLASSIFICATIONS = ("APPOINTMENT", "BILLING", "CLINICAL", "OTHER")

_KEYWORDS = {
    "APPOINTMENT": ("appointment", "schedule", "reschedule", "book", "visit"),
    "BILLING": ("bill", "invoice", "insurance", "payment", "charge"),
    "CLINICAL": ("pain", "medicine", "prescription", "symptom", "dose", "allergy"),
}


class ValidationError(ValueError):
    """The request reached the graph but cannot be processed."""


def validate_node(state: RunState) -> dict[str, object]:
    content = (state.get("content") or "").strip()
    if not content:
        raise ValidationError("content is empty")
    if state.get("patient_id", 0) <= 0:
        raise ValidationError("patient_id must be positive")
    if state.get("conversation_revision", 0) <= 0:
        raise ValidationError("conversation_revision must be positive")
    return {"content": content}


def classify_node(provider: Provider) -> Callable[[RunState], dict[str, object]]:
    """Mock classification, via the provider seam (Round 56 replaces the classifier)."""

    def classify(state: RunState) -> dict[str, object]:
        text = state["content"].lower()
        classification = "OTHER"
        for label, words in _KEYWORDS.items():
            if any(word in text for word in words):
                classification = label
                break
        # The label is keyword-derived for now; the provider call is the seam Round 56
        # replaces with a real classifier. The graph is synchronous, so the call gets its
        # own loop (the endpoint runs in FastAPI's threadpool, which has none).
        asyncio.run(provider.invoke(ProviderRequest(prompt=state["content"], max_output_tokens=16)))
        return {"classification": classification}

    return classify


def draft_node(state: RunState) -> dict[str, object]:
    return {
        "draft": (
            f"Thank you for your message about {state['classification'].lower()}. "
            "The care team will follow up."
        )
    }


def submit_node(
    submit_result: Callable[[RunState], None],
) -> Callable[[RunState], dict[str, object]]:
    def submit(state: RunState) -> dict[str, object]:
        submit_result(state)
        return {"submitted_generation": state["execution_generation"]}

    return submit
