"""Round 56.1 — classification and safety routing.

The model proposes, the rules set a floor, and a failure never reads as routine. Each test
states one of those properties directly, because they are the ones a patient would be hurt
by if they regressed.
"""

from __future__ import annotations

from contextlib import ExitStack

import pytest

from medical_agent.checkpoints import open_checkpointer
from medical_agent.classification.classifier import Classifier
from medical_agent.graph.nodes import ValidationError
from medical_agent.java_client import CallCredentials
from medical_agent.providers.base import ProviderError, ProviderRequest, ProviderResponse
from medical_agent.schemas.classification import Intent, RiskLevel
from medical_agent.schemas.run import RunStartRequest
from medical_agent.worker import AgentWorker


class CannedProvider:
    def __init__(self, answer: object = None, error: Exception | None = None) -> None:
        self.answer = answer
        self.error = error
        self.calls = 0

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        self.calls += 1
        if self.error is not None:
            raise self.error
        return ProviderResponse(text=str(self.answer), provider="stub", model="stub-1",
                                latency_ms=0)

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        return self.invoke_now(request)


def answer(intents: list[str], risk: str = "ROUTINE", confidence: object = 0.9) -> str:
    import json

    return json.dumps({"intents": intents, "risk": risk, "confidence": confidence})


def classify(content: str, provider: CannedProvider | None = None):
    return Classifier(provider or CannedProvider(answer(["OTHER"]))).classify(content)


# ── risk is never downgraded ───────────────────────────────────────

def test_a_mixed_message_keeps_the_highest_risk() -> None:
    # The model sees an appointment request; the rules see chest pain. Both survive.
    decision = classify(
        "I need to reschedule my appointment, also I have chest pain",
        CannedProvider(answer(["APPOINTMENT"])),
    )

    assert decision.risk is RiskLevel.EMERGENCY
    assert Intent.APPOINTMENT in decision.intents
    assert "emergency:chest_pain" in decision.flags
    assert "rule:raised_risk" in decision.flags
    assert decision.requires_clinician


def test_a_model_cannot_lower_a_rule_risk() -> None:
    decision = classify("My face droop started this morning", CannedProvider(answer(["OTHER"])))

    assert decision.risk is RiskLevel.EMERGENCY
    assert "emergency:stroke" in decision.flags


def test_a_clinical_decision_always_needs_a_clinician() -> None:
    decision = classify(
        "Should I stop taking my blood pressure medicine?",
        CannedProvider(answer(["OTHER"])),
    )

    assert "clinical_decision" in decision.flags
    assert decision.requires_clinician
    assert decision.risk is not RiskLevel.ROUTINE


def test_a_routine_message_stays_routine() -> None:
    decision = classify("What are your opening hours?", CannedProvider(answer(["OTHER"])))

    assert decision.risk is RiskLevel.ROUTINE
    assert decision.requires_clinician is False
    assert decision.flags == []


# ── an uncertain answer escalates ──────────────────────────────────

@pytest.mark.parametrize(
    "bad_answer",
    [
        "I think this is about an appointment",
        '{"intents": ["APPOINTMENT"], "risk": "ROUTINE"}',
        '{"intents": ["APPOINTMENT"], "risk": "ROUTINE", "confidence": 0.9, "why": "x"}',
        '{"intents": [], "risk": "ROUTINE", "confidence": 0.9}',
        '{"intents": ["NOT_AN_INTENT"], "risk": "ROUTINE", "confidence": 0.9}',
        '{"intents": ["OTHER"], "risk": "SOMETIMES", "confidence": 0.9}',
        '{"intents": ["OTHER"], "risk": "ROUTINE", "confidence": 1.5}',
        '{"intents": ["OTHER"], "risk": "ROUTINE", "confidence": "high"}',
        # intents must be an array of strings: an object, a bare string or too many entries
        '{"intents": {"BILLING": true}, "risk": "ROUTINE", "confidence": 0.9}',
        '{"intents": "BILLING", "risk": "ROUTINE", "confidence": 0.9}',
        '{"intents": [{"BILLING": true}], "risk": "ROUTINE", "confidence": 0.9}',
        '{"intents": ["OTHER","OTHER","OTHER","OTHER","OTHER","OTHER","OTHER","OTHER"],'
        ' "risk": "ROUTINE", "confidence": 0.9}',
    ],
)
def test_a_malformed_or_uncertain_answer_escalates(bad_answer: str) -> None:
    decision = classify("Can I get a refill?", CannedProvider(bad_answer))

    assert "uncertain_output" in decision.flags
    assert decision.risk in (RiskLevel.URGENT, RiskLevel.EMERGENCY)
    assert decision.confidence == 0.0
    assert decision.requires_clinician


def test_a_provider_failure_escalates_instead_of_guessing() -> None:
    decision = classify("Can I get a refill?", CannedProvider(error=ProviderError("timed out")))

    assert "uncertain_output" in decision.flags
    assert decision.risk is RiskLevel.URGENT
    assert decision.requires_clinician


def test_an_unexpected_provider_error_escalates_too() -> None:
    decision = classify("Can I get a refill?", CannedProvider(error=RuntimeError("boom")))

    assert "uncertain_output" in decision.flags


def test_an_uncertain_answer_still_keeps_an_emergency_floor() -> None:
    decision = classify("I have crushing chest pain", CannedProvider("garbage"))

    assert decision.risk is RiskLevel.EMERGENCY
    assert "emergency:chest_pain" in decision.flags


# ── the contract itself ───────────────────────────────────────────

def test_all_seven_intents_are_accepted() -> None:
    decision = classify(
        "A bit of everything",
        CannedProvider(answer([intent.value for intent in Intent])),
    )

    # OTHER is dropped when something more specific is present, so the answer keeps six.
    assert len(decision.intents) == 6
    assert Intent.OTHER not in decision.intents


def test_the_graph_drafts_a_clinician_review_and_never_a_ready_answer(tmp_path) -> None:
    provider = CannedProvider(answer(["OTHER"]))
    submissions: list[dict[str, object]] = []
    with ExitStack() as stack:
        checkpointer = stack.enter_context(
            open_checkpointer(f"sqlite://{tmp_path / 'routing.sqlite'}")
        )
        worker = AgentWorker(provider, checkpointer, submissions.append)
        state = worker.process(
            RunStartRequest(
                run_id=7,
                message_id=70,
                patient_id=100,
                conversation_revision=4,
                execution_generation=1,
                content="Should I stop taking my blood pressure medicine?",
                request_id="req-safety-1",
            ),
            CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                            outbound_token="out"),
        )

    assert state["requires_clinician"] is True
    assert state["review_required"] is True
    assert "care team will review" in state["draft"]
    assert submissions and submissions[0]["risk"] == "URGENT"
    assert submissions[0]["review_required"] is True


def test_empty_content_is_refused_before_classification(tmp_path) -> None:
    provider = CannedProvider(answer(["OTHER"]))
    with ExitStack() as stack:
        checkpointer = stack.enter_context(
            open_checkpointer(f"sqlite://{tmp_path / 'empty.sqlite'}")
        )
        worker = AgentWorker(provider, checkpointer, lambda state: None)
        with pytest.raises(ValidationError):
            worker.process(
                RunStartRequest(
                    run_id=8, message_id=80, patient_id=100, conversation_revision=4,
                    execution_generation=1, content="   ", request_id="req-safety-2",
                ),
                CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                                outbound_token="out"),
            )

    assert provider.calls == 0


def test_the_mock_provider_routes_instead_of_escalating(tmp_path) -> None:
    # A deployment without a model must still produce a normal routing decision rather than
    # a blanket "uncertain" for every message.
    from medical_agent.providers.mock import MockProvider

    with ExitStack() as stack:
        checkpointer = stack.enter_context(
            open_checkpointer(f"sqlite://{tmp_path / 'mock.sqlite'}")
        )
        worker = AgentWorker(MockProvider(), checkpointer, lambda state: None)
        state = worker.process(
            RunStartRequest(
                run_id=9, message_id=90, patient_id=100, conversation_revision=2,
                execution_generation=1, content="What are your opening hours?",
                request_id="req-safety-3",
            ),
            CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                            outbound_token="out"),
        )

    assert state["risk"] == "ROUTINE"
    assert "uncertain_output" not in state["flags"]


@pytest.mark.parametrize(
    "content",
    [
        "I can\u2019t breathe",          # typographic apostrophe
        "I have chest\npain",           # line break inside the phrase
        "I have chest\u00a0pain",       # non-breaking space
        "I  have   chest pain",          # collapsed spacing
    ],
)
def test_typographic_variants_do_not_bypass_a_rule(content: str) -> None:
    decision = classify(content, CannedProvider(answer(["OTHER"])))

    assert decision.risk is RiskLevel.EMERGENCY
    assert "emergency:chest_pain" in decision.flags or "emergency:breathing" in decision.flags


def test_a_model_classified_clinical_question_needs_a_clinician() -> None:
    # No rule fires; the model's own clinical classification must still require review and
    # must not keep the message at ROUTINE.
    decision = classify("What do you think about this?",
                        CannedProvider(answer(["CLINICAL_QUESTION"])))

    assert decision.requires_clinician is True
    assert decision.risk is not RiskLevel.ROUTINE


def test_a_model_classified_emergency_needs_a_clinician() -> None:
    decision = classify("Something feels very wrong",
                        CannedProvider(answer(["OTHER"], risk="EMERGENCY")))

    assert decision.requires_clinician is True
    assert decision.risk is RiskLevel.EMERGENCY


@pytest.mark.parametrize(
    ("content", "expected"),
    [
        ("Can I book an appointment?", "APPOINTMENT:ROUTINE:DRAFT_READY"),
        ("Where do I send my bill payment?", "BILLING:ROUTINE:DRAFT_READY"),
        ("I need a prescription refill", "PRESCRIPTION_REFILL:ROUTINE:DRAFT_READY"),
    ],
)
def test_the_mock_submits_the_specific_intent_not_other(
    tmp_path, content: str, expected: str
) -> None:
    # The mock answers OTHER; the rules know better, so OTHER must not be the primary intent
    # the graph reports back to Java.
    from medical_agent.providers.mock import MockProvider

    with ExitStack() as stack:
        checkpointer = stack.enter_context(
            open_checkpointer(f"sqlite://{tmp_path / (expected[:10] + '.sqlite')}")
        )
        worker = AgentWorker(MockProvider(), checkpointer, lambda state: None)
        state = worker.process(
            RunStartRequest(
                run_id=11, message_id=110, patient_id=100, conversation_revision=2,
                execution_generation=1, content=content, request_id="req-safety-4",
            ),
            CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                            outbound_token="out"),
        )

    assert state["intents"][0] != "OTHER"
    # The result contract carries these separately now; the expected value keeps the old
    # "intent:risk" shape, so compare the pair (Round 57.1 removed the outcome string).
    assert f"{state['classification']}:{state['risk']}:DRAFT_READY" == expected
