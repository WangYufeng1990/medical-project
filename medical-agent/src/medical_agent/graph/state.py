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
    draft: str
    # Set by the submit node: what a repeated dispatch uses to decide that this generation
    # already produced its draft (Round 55.4's "repeat execution does not create another").
    submitted_generation: int
