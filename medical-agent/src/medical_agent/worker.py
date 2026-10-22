"""The worker: one dispatch call, one graph run (Round 55.4).

A resume is `worker.resume(run_id)`, which continues the graph from its last checkpoint
instead of starting over; that is what makes a crash recoverable rather than repeating the
model call.
"""

from __future__ import annotations

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


class AgentWorker:
    def __init__(
        self,
        provider: Provider,
        checkpointer: BaseCheckpointSaver[Any],
        submit: Callable[[RunState], None] = submit_result,
    ) -> None:
        self._graph = build_graph(provider, submit, checkpointer)

    @staticmethod
    def _config(run_id: int) -> RunnableConfig:
        return {"configurable": {"thread_id": f"run-{run_id}"}}

    def process(self, request: RunStartRequest, credentials: CallCredentials) -> RunState:
        config = self._config(request.run_id)
        snapshot = self._graph.get_state(config)
        values = dict(snapshot.values or {})
        if values.get("submitted_generation") == request.execution_generation:
            # A repeated dispatch of the same attempt must not produce a second draft.
            return cast(RunState, values)
        if values and snapshot.next:
            # An attempt that stopped part-way (a crash, an unreachable Java) is reconciled by
            # resuming its checkpoint rather than starting the work over.
            return self.resume(request.run_id, credentials)

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

    def resume(self, run_id: int, credentials: CallCredentials) -> RunState:
        token = use_credentials(credentials)
        try:
            return cast(RunState, self._graph.invoke(None, self._config(run_id)))
        finally:
            reset_credentials(token)
