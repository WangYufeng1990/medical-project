"""The minimal graph's nodes (Round 56.1): validate, classify, draft, submit."""

from collections.abc import Callable

from medical_agent.classification.classifier import Classifier
from medical_agent.graph.state import RunState


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


def classify_node(classifier: Classifier) -> Callable[[RunState], dict[str, object]]:
    def classify(state: RunState) -> dict[str, object]:
        decision = classifier.classify(state["content"])
        return {
            "classification": decision.intents[0].value,
            "intents": [intent.value for intent in decision.intents],
            "risk": decision.risk.value,
            "flags": list(decision.flags),
            "confidence": decision.confidence,
            "requires_clinician": decision.requires_clinician,
        }

    return classify


def draft_node(state: RunState) -> dict[str, object]:
    # Every v1 draft is for staff review; a clinician-required message says so in the text
    # rather than producing an answer that could be mistaken for clinical advice (56.4 and
    # Round 57 enforce publication separately).
    if state.get("requires_clinician"):
        draft = (
            "A member of the care team will review your message and respond. "
            "If this is an emergency, call 911."
        )
    else:
        draft = (
            f"Thank you for your message about {state['classification'].lower()}. "
            "The care team will follow up."
        )
    return {"draft": draft, "review_required": True}


def submit_node(
    submit_result: Callable[[RunState], None],
) -> Callable[[RunState], dict[str, object]]:
    def submit(state: RunState) -> dict[str, object]:
        submit_result(state)
        return {"submitted_generation": state["execution_generation"]}

    return submit
