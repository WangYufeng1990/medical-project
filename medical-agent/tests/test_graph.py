"""Round 55.4 — the minimal persistent graph.

What matters here is persistence, not the mock's cleverness: a resumed run must not repeat
work the checkpoint already records, and the checkpoint must not contain the capability.
"""

from __future__ import annotations

import threading
from contextlib import ExitStack
from pathlib import Path

import pytest

from medical_agent.checkpoints import open_checkpointer
from medical_agent.graph.nodes import ValidationError
from medical_agent.java_client import CallCredentials
from medical_agent.providers.base import ProviderRequest, ProviderResponse
from medical_agent.schemas.run import RunStartRequest
from medical_agent.worker import AgentWorker, StaleExecution

CAPABILITY = "capability-marker-4f1d9c2b"
CLASSIFIER_ANSWER = '{"intents": ["APPOINTMENT"], "risk": "ROUTINE", "confidence": 0.9}'


class RecordingProvider:
    def __init__(self) -> None:
        self.calls = 0

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        self.calls += 1
        return ProviderResponse(text=CLASSIFIER_ANSWER, provider="mock",
                                model="mock-1", latency_ms=1)

    def invoke_now(self, request: ProviderRequest) -> ProviderResponse:
        import asyncio

        return asyncio.run(self.invoke(request))


class RecordingSubmit:
    def __init__(self, fail_first: bool = False, fail_at: tuple[int, ...] = ()) -> None:
        self.fail_first = fail_first
        self.fail_at = fail_at
        self.submissions: list[dict[str, object]] = []

    def __call__(self, state: dict[str, object]) -> None:
        self.submissions.append(dict(state))
        call = len(self.submissions)
        if (self.fail_first and call == 1) or call in self.fail_at:
            raise RuntimeError("Java was unreachable")


def start_request(
    content: str = "Can I reschedule my appointment?", execution_generation: int = 1
) -> RunStartRequest:
    return RunStartRequest(
        run_id=41,
        message_id=9001,
        patient_id=100,
        conversation_revision=12,
        execution_generation=execution_generation,
        content=content,
        request_id="req-0123456789",
    )


def credentials() -> CallCredentials:
    return CallCredentials(capability=CAPABILITY, java_base_url="http://127.0.0.1:1",
                           outbound_token="outbound")


@pytest.fixture()
def checkpointer(tmp_path: Path):
    with ExitStack() as stack:
        yield stack.enter_context(open_checkpointer(f"sqlite://{tmp_path / 'checkpoints.sqlite'}"))


def test_the_graph_classifies_drafts_and_submits(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit()
    worker = AgentWorker(provider, checkpointer, submit)

    state = worker.process(start_request(), credentials())

    assert state["classification"] == "APPOINTMENT"
    assert "care team" in state["draft"]
    assert len(submit.submissions) == 1
    assert submit.submissions[0]["execution_generation"] == 1
    assert provider.calls == 1


def test_a_resumed_run_does_not_repeat_the_classification(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit(fail_first=True)
    worker = AgentWorker(provider, checkpointer, submit)

    with pytest.raises(RuntimeError):
        worker.process(start_request(), credentials())
    completed = worker.resume(41, credentials())

    assert completed["draft"], "the resumed run finishes"
    assert provider.calls == 1, "the checkpointed classification must not run again"
    assert len(submit.submissions) == 2, "the failed submission is retried once"


def test_empty_content_is_refused_before_any_model_work(checkpointer) -> None:
    provider = RecordingProvider()
    worker = AgentWorker(provider, checkpointer, RecordingSubmit())

    with pytest.raises(ValidationError):
        worker.process(start_request(content="   "), credentials())

    assert provider.calls == 0


def test_the_checkpoint_holds_no_capability(tmp_path: Path) -> None:
    path = tmp_path / "secrets.sqlite"
    with open_checkpointer(f"sqlite://{path}") as checkpointer:
        worker = AgentWorker(RecordingProvider(), checkpointer, RecordingSubmit())
        worker.process(start_request(), credentials())

    raw = path.read_bytes()
    assert CAPABILITY.encode() not in raw, "the capability must never reach a checkpoint"
    assert b"outbound" not in raw


def test_a_repeat_dispatch_does_not_produce_a_second_draft(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit()
    worker = AgentWorker(provider, checkpointer, submit)
    request = start_request()

    first = worker.process(request, credentials())
    second = worker.process(request, credentials())

    assert first["draft"] == second["draft"]
    assert provider.calls == 1, "the classification must not run twice"
    assert len(submit.submissions) == 1, "and nothing is submitted twice"


def test_an_unfinished_run_is_resumed_by_the_repeat_dispatch(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit(fail_first=True)
    worker = AgentWorker(provider, checkpointer, submit)
    request = start_request()

    with pytest.raises(RuntimeError):
        worker.process(request, credentials())
    recovered = worker.process(request, credentials())

    assert recovered["draft"], "the retry finishes the work the crash interrupted"
    assert provider.calls == 1, "without classifying again"
    assert len(submit.submissions) == 2, "the failed submission and its retry"


def test_a_newer_generation_takes_over_a_failed_submission(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit(fail_first=True)
    worker = AgentWorker(provider, checkpointer, submit)

    with pytest.raises(RuntimeError):
        worker.process(start_request(), credentials())  # generation 1 dies at submit
    recovered = worker.process(start_request(execution_generation=2), credentials())

    assert recovered["draft"], "the work is kept across the retry"
    assert provider.calls == 1, "Java's retry must not classify again"
    assert [state["execution_generation"] for state in submit.submissions] == [1, 2], (
        "the retried submission must carry the generation Java is waiting for"
    )
    assert recovered["submitted_generation"] == 2, "and the checkpoint records that submission"


def test_an_older_generation_cannot_overwrite_newer_work(checkpointer) -> None:
    worker = AgentWorker(RecordingProvider(), checkpointer, RecordingSubmit())
    worker.process(start_request(execution_generation=2), credentials())

    with pytest.raises(StaleExecution):
        worker.process(start_request(execution_generation=1), credentials())


def test_two_concurrent_dispatches_of_one_run_do_the_work_once(checkpointer) -> None:
    provider = RecordingProvider()
    submit = RecordingSubmit()
    worker = AgentWorker(provider, checkpointer, submit)
    barrier = threading.Barrier(2)
    outcomes: list[object] = []

    def dispatch() -> None:
        barrier.wait(timeout=10)
        outcomes.append(worker.process(start_request(), credentials()))

    threads = [threading.Thread(target=dispatch) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=30)

    assert len(outcomes) == 2, "both callers got an answer"
    assert provider.calls == 1, "the classification ran once"
    assert len(submit.submissions) == 1, "and the result was submitted once"


def test_a_failed_submission_after_taking_over_a_generation_retries_only_the_submission(
    checkpointer,
) -> None:
    # The gap this closes: generation 1 finished and submitted; generation 2 takes the
    # checkpoint over but its submission fails; the same generation 2 arrives again. The
    # classifier and draft must not run again - only the submission is retried.
    provider = RecordingProvider()
    submit = RecordingSubmit(fail_at=(2,))
    worker = AgentWorker(provider, checkpointer, submit)

    first = worker.process(start_request(), credentials())  # generation 1, submitted
    with pytest.raises(RuntimeError):
        worker.process(start_request(execution_generation=2), credentials())
    recovered = worker.process(start_request(execution_generation=2), credentials())

    assert provider.calls == 1, "the draft must not be recomputed after the take-over"
    assert recovered["draft"] == first["draft"], "the same draft, submitted again"
    assert [state["execution_generation"] for state in submit.submissions] == [1, 2, 2]
    assert recovered["submitted_generation"] == 2


def test_a_new_generation_resumes_a_failed_classification(
    checkpointer, monkeypatch: pytest.MonkeyPatch
) -> None:
    # A provider failure no longer stops the graph (it escalates instead), so this failure
    # is injected at the classifier node: what matters is that the checkpoint stops there,
    # and that generation 2 runs the classification rather than declaring the draft complete.
    from medical_agent.classification.classifier import Classifier

    original = Classifier.classify
    attempts = {"count": 0}

    def failing_once(self, content: str):
        attempts["count"] += 1
        if attempts["count"] == 1:
            raise RuntimeError("the classifier exploded")
        return original(self, content)

    monkeypatch.setattr(Classifier, "classify", failing_once)
    provider = RecordingProvider()
    submit = RecordingSubmit()
    worker = AgentWorker(provider, checkpointer, submit)

    with pytest.raises(RuntimeError):
        worker.process(start_request(), credentials())
    recovered = worker.process(start_request(execution_generation=2), credentials())

    assert attempts["count"] == 2, "the classification ran again under the new generation"
    assert recovered["classification"] == "APPOINTMENT"
    assert recovered["draft"], "the draft exists before anything is submitted"
    assert [state["execution_generation"] for state in submit.submissions] == [2]


def test_a_new_generation_resumes_from_the_draft_node(checkpointer) -> None:
    # A checkpoint that finished classifying but not drafting: the take-over must run the
    # draft node, not jump to the submission.
    submit = RecordingSubmit()
    worker = AgentWorker(RecordingProvider(), checkpointer, submit)
    config = {"configurable": {"thread_id": "run-41"}}
    worker._graph.update_state(  # type: ignore[attr-defined]
        config,
        {
            "run_id": 41,
            "message_id": 9001,
            "patient_id": 100,
            "conversation_revision": 12,
            "execution_generation": 1,
            "content": "Can I reschedule my appointment?",
            "classification": "APPOINTMENT",
        },
        as_node="classify_message",
    )

    recovered = worker.process(start_request(execution_generation=2), credentials())

    assert recovered["draft"], "the pending draft node ran"
    assert [state["execution_generation"] for state in submit.submissions] == [2]
