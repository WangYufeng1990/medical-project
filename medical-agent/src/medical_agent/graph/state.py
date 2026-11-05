"""The graph's state (Round 55.4).

Deliberately data-only and secret-free: the capability token never enters the state, so it
cannot reach a checkpoint. It travels in a context variable for the duration of one call.
"""

from typing import TypedDict


class RunState(TypedDict, total=False):
    run_id: int
    message_id: int
    patient_id: int
    conversation_revision: int
    execution_generation: int
    content: str
    # The routing decision (Round 56.1): the primary intent is kept under "classification"
    # because the draft and the result use it.
    classification: str
    intents: list[str]
    risk: str
    flags: list[str]
    confidence: float
    requires_clinician: bool
    review_required: bool
    # Which kind of draft the working state is producing (see medical_agent.drafting).
    kind: str
    # Retrieved policy evidence, and the citations the server built from it (Round 56.3).
    evidence: list[dict[str, object]]
    citations: list[dict[str, object]]
    # The citations the draft actually relies on (empty for routing and fallback drafts).
    adopted_citations: list[dict[str, object]]
    # What the validator thought of the draft:
    # {"ok": bool, "kind": str, "rejected_reasons": [...], "reasons": [...]}
    validation: dict[str, object]
    # The read-only tool boundary (Round 58.2): the call counter and the run's start are part of the
    # state, so a resumed run continues its budget instead of starting a fresh one.
    tool_calls: int
    tool_started_at: float
    tool_result: dict[str, object] | None
    tool_needs_human: bool
    draft: str
    # Set by the submit node: what a repeated dispatch uses to decide that this generation
    # already produced its draft (Round 55.4's "repeat execution does not create another").
    submitted_generation: int
