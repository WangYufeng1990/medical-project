"""Builds the minimal persistent graph (Round 55.4).

The plan's full path (intake -> classify -> tool plan -> retrieve -> draft -> policy -> review)
arrives across Rounds 56-58; this one carries the same seams with a Mock classifier so the
checkpoint and recovery behaviour can be tested now.
"""

from collections.abc import Callable
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from medical_agent.graph.nodes import classify_node, draft_node, submit_node, validate_node
from medical_agent.graph.state import RunState
from medical_agent.providers.base import Provider


def build_graph(
    provider: Provider,
    submit_result: Callable[[RunState], None],
    checkpointer: BaseCheckpointSaver[Any],
) -> CompiledStateGraph:
    graph = StateGraph(RunState)
    graph.add_node("validate_content", validate_node)
    graph.add_node("classify_message", classify_node(provider))
    graph.add_node("draft_reply", draft_node)
    graph.add_node("submit_result", submit_node(submit_result))
    graph.add_edge(START, "validate_content")
    graph.add_edge("validate_content", "classify_message")
    graph.add_edge("classify_message", "draft_reply")
    graph.add_edge("draft_reply", "submit_result")
    graph.add_edge("submit_result", END)
    return graph.compile(checkpointer=checkpointer)
