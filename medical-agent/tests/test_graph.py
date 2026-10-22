"""Round 55.4 — the minimal persistent graph.

What matters here is persistence, not the mock's cleverness: a resumed run must not repeat
work the checkpoint already records, and the checkpoint must not contain the capability.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from medical_agent.checkpoints import open_checkpointer
from medical_agent.graph.nodes import ValidationError
from medical_agent.java_client import CallCredentials
from medical_agent.providers.base import ProviderRequest, ProviderResponse
from medical_agent.schemas.run import RunStartRequest
from medical_agent.worker import AgentWorker

CAPABILITY = "capability-marker-4f1d9c2b"


class RecordingProvider:
    def __init__(self) -> None:
        self.calls = 0

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        self.calls += 1
        return ProviderResponse(text="mock:classification", provider="mock",
                                model="mock-1", latency_ms=1)


class RecordingSubmit:
    def __init__(self, fail_first: bool = False) -> None:
        self.fail_first = fail_first
        self.submissions: list[dict[str, object]] = []

    def __call__(self, state: dict[str, object]) -> None:
        if self.fail_first and not self.submissions:
            self.submissions.append(dict(state))
            raise RuntimeError("Java was unreachable")
        self.submissions.append(dict(state))


def start_request(content: str = "Can I reschedule my appointment?") -> RunStartRequest:
    return RunStartRequest(
        run_id=41,
        message_id=9001,
        patient_id=100,
        conversation_revision=12,
        execution_generation=1,
        content=content,
        request_id="req-0123456789",
    )


def credentials() -> CallCredentials:
    return CallCredentials(capability=CAPABILITY, java_base_url="http://127.0.0.1:1",
                           outbound_token="outbound")


@pytest.fixture()
def checkpointer(tmp_path: Path):
    return open_checkpointer(f"sqlite://{tmp_path / 'checkpoints.sqlite'}")


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
    checkpointer = open_checkpointer(f"sqlite://{path}")
    worker = AgentWorker(RecordingProvider(), checkpointer, RecordingSubmit())

    worker.process(start_request(), credentials())
    checkpointer.conn.close()  # type: ignore[attr-defined]

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
