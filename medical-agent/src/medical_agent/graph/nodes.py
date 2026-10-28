"""The minimal graph's nodes (Round 56.1): validate, classify, draft, submit."""

from collections.abc import Callable

from medical_agent.classification.classifier import Classifier
from medical_agent.drafting import build_draft, safe_fallback
from medical_agent.graph.state import RunState
from medical_agent.kb.citations import Citation, build_citations, verify_citations
from medical_agent.kb.retrieval import Retriever
from medical_agent.validation import contains_instructions, validate_draft


class ValidationError(ValueError):
    """The request reached the graph but cannot be processed."""


def validate_node(state: RunState) -> dict[str, object]:
    content = (state.get("content") or "").strip()
    if not content:
        raise ValidationError("content is empty")
    if state.get("patient_id", 0) <= 0:
        raise ValidationError("patient_id must be positive")
    if state.get("conversation_revision", 0) <= 0:
        raise ValidationError("conversation_revision must be positive")
    return {"content": content}


def classify_node(classifier: Classifier) -> Callable[[RunState], dict[str, object]]:
    def classify(state: RunState) -> dict[str, object]:
        decision = classifier.classify(state["content"])
        return {
            "classification": decision.intents[0].value,
            "intents": [intent.value for intent in decision.intents],
            "risk": decision.risk.value,
            "flags": list(decision.flags),
            "confidence": decision.confidence,
            "requires_clinician": decision.requires_clinician,
        }

    return classify


def retrieve_node(
    retriever: Retriever | None,
) -> Callable[[RunState], dict[str, object]]:
    """Attach the policy evidence a draft may rely on, or say that there is none."""

    def retrieve(state: RunState) -> dict[str, object]:
        if retriever is None:
            # No approved corpus is loaded: the same "I found nothing" path as a zero-hit
            # search, so the draft never pretends to know a policy it was not given.
            return {
                "evidence": [],
                "citations": [],
                "flags": [*state.get("flags", []), "evidence_missing"],
            }
        result = retriever.search(state["content"], limit=4)
        flags = list(state.get("flags", []))
        if result.empty:
            flags.append("evidence_missing")
            return {"evidence": [], "citations": [], "flags": flags}
        citations = build_citations(result.hits)
        verify_citations(citations, result.hits)
        if result.conflict:
            flags.append("evidence_conflict")
        evidence = [
            {
                "chunk_id": hit.chunk.id,
                "document_id": hit.chunk.document_id,
                "text": hit.chunk.text,
                "score": hit.score,
            }
            for hit in result.hits
        ]
        return {
            "evidence": evidence,
            "citations": [
                {
                    "chunk_id": citation.chunk_id,
                    "document_id": citation.document_id,
                    "document_version": citation.document_version,
                    "title": citation.title,
                    "heading_path": list(citation.heading_path),
                    "content_hash": citation.content_hash,
                }
                for citation in citations
            ],
            "flags": flags,
        }

    return retrieve


def draft_node(state: RunState) -> dict[str, object]:
    """Build the draft from evidence, validate it, and fall back if anything is wrong."""
    flags = list(state.get("flags", []))
    evidence = list(state.get("evidence") or [])
    citations = list(state.get("citations") or [])

    # A chunk that tries to give the agent instructions is evidence of tampering, not a policy:
    # it is dropped before drafting, and the fact that it was there stays on the record.
    trusted = [chunk for chunk in evidence if not contains_instructions(str(chunk["text"]))]
    if len(trusted) != len(evidence):
        flags.append("untrusted_evidence")
        allowed = {str(chunk["chunk_id"]) for chunk in trusted}
        evidence = trusted
        citations = [item for item in citations if str(item["chunk_id"]) in allowed]

    working: RunState = {**state, "evidence": evidence, "citations": citations, "flags": flags}
    draft = build_draft(working)
    validation = validate_draft(draft, working)
    if not validation.ok:
        flags.append("draft_rejected")
        draft = safe_fallback(working)
        validation = validate_draft(draft, working)
    return {
        "draft": draft,
        "review_required": True,
        "evidence": evidence,
        "citations": citations,
        "flags": flags,
        "validation": {"ok": validation.ok, "reasons": list(validation.reasons)},
    }


def _citation_from(item: dict[str, object]) -> Citation:
    heading = item.get("heading_path")
    return Citation(
        chunk_id=str(item.get("chunk_id")),
        document_id=str(item.get("document_id")),
        document_version=int(str(item.get("document_version"))),
        title=str(item.get("title")),
        heading_path=tuple(str(part) for part in heading) if isinstance(heading, list) else (),
        content_hash=str(item.get("content_hash")),
    )


def submit_node(
    submit_result: Callable[[RunState], None],
) -> Callable[[RunState], dict[str, object]]:
    def submit(state: RunState) -> dict[str, object]:
        submit_result(state)
        return {"submitted_generation": state["execution_generation"]}

    return submit
