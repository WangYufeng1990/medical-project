"""The committed JSON Schemas and the fixtures both languages read (Round 55.1).

The Pydantic models are the source of truth: these documents are generated from them,
and ``export_contracts.py`` rewrites the files under ``contracts/agent/v1/``. The test
``test_contracts.py`` fails when the files and the models drift apart.
"""

from __future__ import annotations

import json
from typing import Any

from medical_agent.schemas.run import ErrorResponse, RunStartRequest, RunStatusResponse

SCHEMA_MODELS: dict[str, type[RunStartRequest | RunStatusResponse | ErrorResponse]] = {
    "run-start.request.schema.json": RunStartRequest,
    "run-status.response.schema.json": RunStatusResponse,
    "error.response.schema.json": ErrorResponse,
}


def schema_documents() -> dict[str, dict[str, Any]]:
    """The generated schema per file, in serialization form (what goes on the wire)."""
    return {
        name: model.model_json_schema(mode="serialization")
        for name, model in SCHEMA_MODELS.items()
    }


def dumps(document: dict[str, Any]) -> str:
    """One canonical rendering, so a regenerated file differs only when the schema does."""
    return json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
