"""The worker: one dispatch call, one graph run (Round 55.4).

Recovery has three cases, and they are not the same:
* the same generation already submitted — return it, do nothing;
* an unfinished run — resume its checkpoint instead of starting over;
* a newer generation than the checkpoint — Java retried, so the finished classification and
  draft are reused and only the execution generation is adopted before resuming; submitting
  the old generation would be refused by Java (409).

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

from medical_agent.graph.builder import build_graph
from medical_agent.graph.state import RunState
from medical_agent.java_client import (
    CallCredentials,
    reset_credentials,
    submit_result,
    use_credentials,
)
from medical_agent.providers.base import Provider
from medical_agent.schemas.run import RunStartRequest


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
    ) -> None:
        self._graph = build_graph(provider, submit, checkpointer)
        self._submit = submit
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
                return self._adopt_generation(request, credentials, values, snapshot.next)
            if snapshot.next:
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

    def _adopt_generation(
        self,
        request: RunStartRequest,
        credentials: CallCredentials,
        values: dict[str, Any],
        pending: tuple[str, ...],
    ) -> RunState:
        """A newer Java generation takes over the work this checkpoint already holds."""
        config = self._config(request.run_id)
        self._graph.update_state(
            config, {"execution_generation": request.execution_generation}
        )
        if pending:
            return self._resume_locked(request.run_id, credentials)

        # The graph finished its work under the old generation; only the submission is
        # repeated, with the generation Java is waiting for.
        adopted: RunState = {
            **cast(RunState, values),
            "execution_generation": request.execution_generation,
        }
        token = use_credentials(credentials)
        try:
            self._submit(adopted)
        finally:
            reset_credentials(token)
        self._graph.update_state(
            config,
            {
                "execution_generation": request.execution_generation,
                "submitted_generation": request.execution_generation,
            },
        )
        return cast(RunState, {**values, "execution_generation": request.execution_generation,
                               "submitted_generation": request.execution_generation})

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
