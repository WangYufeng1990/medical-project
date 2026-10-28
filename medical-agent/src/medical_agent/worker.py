"""The worker: one dispatch call, one graph run (Round 55.4).

Recovery has three cases, and they are not the same:
* the same generation already submitted — return it, do nothing;
* an unfinished run — resume its checkpoint instead of starting over;
* a newer generation than the checkpoint — Java retried. What is reused depends on how far the
  checkpoint got: a draft that exists is kept and only the submission is repeated, while a
  checkpoint stopped at validation, classification or drafting resumes those nodes with the new
  generation. "There is a checkpoint" is not evidence that the draft exists.

Because the submission runs inside the graph, re-entering it after the draft node is what
makes a failed submission recoverable: the checkpoint holds a pending ``submit_result`` task,
so the next request resumes that node instead of starting the graph over.

One lock per run makes the check-then-act step atomic inside this process, because FastAPI
runs the endpoint in a thread pool and Java's claim protection does not cover Python. A stale
request (older generation than the checkpoint) refuses rather than overwriting newer work.
"""

from __future__ import annotations

import threading
from collections.abc import Callable
from typing import Any, cast

from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.base import BaseCheckpointSaver

from medical_agent.classification.classifier import Classifier
from medical_agent.graph.builder import DRAFT_NODE, RETRIEVE_NODE, SUBMIT_NODE, build_graph
from medical_agent.graph.state import RunState
from medical_agent.java_client import (
    CallCredentials,
    reset_credentials,
    submit_result,
    use_credentials,
)
from medical_agent.kb.retrieval import Retriever
from medical_agent.providers.base import Provider
from medical_agent.schemas.run import RunStartRequest


def _validated(values: dict[str, Any]) -> bool:
    validation = values.get("validation")
    return isinstance(validation, dict) and validation.get("ok") is True


class StaleExecution(RuntimeError):
    """The request is older than the work already recorded for this run."""


class CheckpointMismatch(RuntimeError):
    """The checkpoint belongs to different run identity fields than the request."""


class AgentWorker:
    def __init__(
        self,
        provider: Provider,
        checkpointer: BaseCheckpointSaver[Any],
        submit: Callable[[RunState], None] = submit_result,
        max_prompt_chars: int = 8000,
        retriever: Retriever | None = None,
    ) -> None:
        self._graph = build_graph(
            Classifier(provider, max_prompt_chars), retriever, submit, checkpointer
        )
        self._locks: dict[int, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    @staticmethod
    def _config(run_id: int) -> RunnableConfig:
        return {"configurable": {"thread_id": f"run-{run_id}"}}

    def _lock(self, run_id: int) -> threading.Lock:
        with self._locks_guard:
            return self._locks.setdefault(run_id, threading.Lock())

    def process(self, request: RunStartRequest, credentials: CallCredentials) -> RunState:
        with self._lock(request.run_id):
            return self._process_locked(request, credentials)

    def resume(self, run_id: int, credentials: CallCredentials) -> RunState:
        with self._lock(run_id):
            return self._resume_locked(run_id, credentials)

    def _process_locked(
        self, request: RunStartRequest, credentials: CallCredentials
    ) -> RunState:
        config = self._config(request.run_id)
        snapshot = self._graph.get_state(config)
        values = dict(snapshot.values or {})

        if values:
            self._require_same_run(values, request)
            recorded = int(values.get("execution_generation", 0))
            if recorded > request.execution_generation:
                raise StaleExecution(
                    f"run {request.run_id} already recorded generation {recorded}"
                )
            if values.get("submitted_generation") == request.execution_generation:
                return cast(RunState, values)
            if recorded < request.execution_generation:
                return self._take_over(request, credentials, values, snapshot.next)
            if snapshot.next:
                return self._resume_or_revalidate(
                    request.run_id, request.execution_generation, values, snapshot.next, credentials
                )
            if not values.get("draft"):
                # Nothing computed and nothing pending: recompute rather than pretend.
                pass
            else:
                # Same generation, work finished, submission not recorded: a previous attempt
                # died between adopting the generation and submitting it. Re-enter the submit
                # node rather than starting the graph again.
                self._schedule_submission(request.run_id, request.execution_generation)
                return self._resume_locked(request.run_id, credentials)

        token = use_credentials(credentials)
        try:
            return cast(
                RunState,
                self._graph.invoke(
                    {
                        "run_id": request.run_id,
                        "message_id": request.message_id,
                        "patient_id": request.patient_id,
                        "conversation_revision": request.conversation_revision,
                        "execution_generation": request.execution_generation,
                        "content": request.content,
                    },
                    config,
                ),
            )
        finally:
            reset_credentials(token)

    def _take_over(
        self,
        request: RunStartRequest,
        credentials: CallCredentials,
        values: dict[str, Any],
        pending: tuple[str, ...],
    ) -> RunState:
        """A newer generation continues the checkpoint where it actually stopped."""
        generation = request.execution_generation
        if values.get("draft") and not pending:
            # The computation finished; only the submission is outstanding - unless this
            # checkpoint predates the validator, in which case drafting and validation run again.
            if _validated(values):
                self._schedule_submission(request.run_id, generation)
            else:
                self._reenter_at_retrieval(request.run_id, generation)
            return self._resume_locked(request.run_id, credentials)
        if pending:
            # Validation, classification or drafting is still to do: adopt the generation and
            # let the checkpoint's pending node run, rather than declaring the draft complete.
            return self._resume_or_revalidate(
                request.run_id, generation, values, pending, credentials
            )
        # No draft and nothing pending: there is no computed work to keep, so the graph is
        # started again under the new generation.
        token = use_credentials(credentials)
        try:
            return cast(
                RunState,
                self._graph.invoke(
                    {
                        "run_id": request.run_id,
                        "message_id": request.message_id,
                        "patient_id": request.patient_id,
                        "conversation_revision": request.conversation_revision,
                        "execution_generation": generation,
                        "content": request.content,
                    },
                    self._config(request.run_id),
                ),
            )
        finally:
            reset_credentials(token)

    def _resume_or_revalidate(
        self,
        run_id: int,
        generation: int,
        values: dict[str, Any],
        pending: tuple[str, ...],
        credentials: CallCredentials,
    ) -> RunState:
        """Resume the pending work, re-entering drafting when the checkpoint was never validated."""
        if pending == (SUBMIT_NODE,):
            # Adopt the generation but keep submission as the next step; re-entering at the
            # draft node is what keeps the pending submit intact. A checkpoint that was never
            # validated goes back through drafting instead.
            if _validated(values):
                self._schedule_submission(run_id, generation)
            else:
                self._reenter_at_retrieval(run_id, generation)
        else:
            self._graph.update_state(self._config(run_id), {"execution_generation": generation})
        return self._resume_locked(run_id, credentials)

    def _reenter_at_retrieval(self, run_id: int, generation: int) -> None:
        """A checkpoint from before validation existed: draft and validate it again."""
        self._graph.update_state(
            self._config(run_id),
            {"execution_generation": generation},
            as_node=RETRIEVE_NODE,
        )

    def _schedule_submission(self, run_id: int, generation: int) -> None:
        """Re-enter the graph at the draft node, so the next step is the submit node."""
        self._graph.update_state(
            self._config(run_id),
            {"execution_generation": generation},
            as_node=DRAFT_NODE,
        )

    def _resume_locked(self, run_id: int, credentials: CallCredentials) -> RunState:
        token = use_credentials(credentials)
        try:
            return cast(RunState, self._graph.invoke(None, self._config(run_id)))
        finally:
            reset_credentials(token)

    @staticmethod
    def _require_same_run(values: dict[str, Any], request: RunStartRequest) -> None:
        for field, expected in (
            ("run_id", request.run_id),
            ("message_id", request.message_id),
            ("patient_id", request.patient_id),
            ("conversation_revision", request.conversation_revision),
        ):
            if values.get(field) != expected:
                raise CheckpointMismatch(
                    f"checkpoint {field}={values.get(field)!r} does not match the request "
                    f"({expected!r})"
                )
