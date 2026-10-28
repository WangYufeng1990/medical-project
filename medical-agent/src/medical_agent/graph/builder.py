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

from medical_agent.classification.classifier import Classifier
from medical_agent.graph.nodes import (
    classify_node,
    draft_node,
    retrieve_node,
    submit_node,
    validate_node,
)
from medical_agent.graph.state import RunState
from medical_agent.kb.retrieval import Retriever

# The nodes a resumed run may need to re-enter (see the worker's legacy-checkpoint path).
# Scheduling *from* a node's predecessor makes that node run; scheduling from the node itself
# would declare it finished and skip it.
CLASSIFY_NODE = "classify_message"
RETRIEVE_NODE = "retrieve_evidence"
SUBMIT_NODE = "submit_result"

# The node the submission hangs off: re-entering the graph after it runs the submit node
# again, so a repeated submission is checkpointed rather than performed outside the graph.
DRAFT_NODE = "draft_reply"


def build_graph(
    classifier: Classifier,
    retriever: Retriever | None,
    submit_result: Callable[[RunState], None],
    checkpointer: BaseCheckpointSaver[Any],
) -> CompiledStateGraph:
    graph = StateGraph(RunState)
    graph.add_node("validate_content", validate_node)
    graph.add_node("classify_message", classify_node(classifier))
    graph.add_node("retrieve_evidence", retrieve_node(retriever))
    graph.add_node(DRAFT_NODE, draft_node)
    graph.add_node("submit_result", submit_node(submit_result))
    graph.add_edge(START, "validate_content")
    graph.add_edge("validate_content", "classify_message")
    graph.add_edge("classify_message", "retrieve_evidence")
    graph.add_edge("retrieve_evidence", DRAFT_NODE)
    graph.add_edge(DRAFT_NODE, "submit_result")
    graph.add_edge("submit_result", END)
    return graph.compile(checkpointer=checkpointer)
