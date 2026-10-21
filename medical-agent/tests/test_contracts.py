"""The Java ⇄ Python contract (Round 55.1).

Both sides read ``contracts/agent/v1/``. These tests keep the committed schemas in
step with the models, and prove the fixtures mean what the contract says.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from medical_agent.contracts import SCHEMA_MODELS, dumps, schema_documents
from medical_agent.schemas.run import (
    CONTRACT_VERSION,
    ErrorResponse,
    RunStartRequest,
    RunState,
    RunStatusResponse,
)

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "agent" / "v1"
FIXTURES = CONTRACTS / "fixtures"

MODELS = {
    "run-start": RunStartRequest,
    "run-status": RunStatusResponse,
    "error": ErrorResponse,
}


def fixture_files() -> list[Path]:
    return sorted(FIXTURES.glob("*.json"))


def test_the_contract_is_versioned() -> None:
    assert CONTRACT_VERSION == "v1"
    assert (CONTRACTS / "run-start.request.schema.json").exists()
    assert len(fixture_files()) >= 5


def test_committed_schemas_match_the_models() -> None:
    """A regenerated file may differ only when the schema really changed."""
    for name, document in schema_documents().items():
        committed = (CONTRACTS / name).read_text(encoding="utf-8")
        assert committed == dumps(document), (
            f"{name} is out of date; run: .venv/bin/python scripts/export_contracts.py"
        )


def test_every_schema_forbids_unknown_fields() -> None:
    for name, document in schema_documents().items():
        assert document["additionalProperties"] is False, f"{name} accepts unknown fields"


@pytest.mark.parametrize("path", [p for p in fixture_files() if ".valid." in p.name])
def test_valid_fixtures_are_accepted(path: Path) -> None:
    model = MODELS[path.name.split(".")[0]]
    payload = json.loads(path.read_text(encoding="utf-8"))

    parsed = model.model_validate(payload)

    assert parsed.contract_version == "v1"


@pytest.mark.parametrize("path", [p for p in fixture_files() if ".invalid-" in p.name])
def test_invalid_fixtures_are_refused(path: Path) -> None:
    model = MODELS[path.name.split(".")[0]]
    payload = json.loads(path.read_text(encoding="utf-8"))

    with pytest.raises(ValidationError):
        model.model_validate(payload)


def test_the_status_fixture_uses_a_state_from_the_contract() -> None:
    payload = json.loads((FIXTURES / "run-status.valid.json").read_text(encoding="utf-8"))

    assert payload["state"] in {state.value for state in RunState}
    assert {state.value for state in RunState} == {
        "PENDING",
        "RUNNING",
        "WAITING_REVIEW",
        "COMPLETED",
        "FAILED",
        "CANCELLED",
        "SUPERSEDED",
    }


def test_the_start_fixture_carries_server_owned_identity_only() -> None:
    payload = json.loads((FIXTURES / "run-start.valid.json").read_text(encoding="utf-8"))

    # The patient arrives from Java's run context; nothing in the payload may carry
    # model-produced identity or an instruction to widen access.
    assert payload["sender_type"] == "PATIENT"
    assert "tools" not in payload
    assert "patient_name" not in payload


def test_schema_files_are_listed_in_the_contract_module() -> None:
    assert set(SCHEMA_MODELS) == {p.name for p in CONTRACTS.glob("*.schema.json")}


def test_identity_fields_are_not_coerced_from_other_json_types() -> None:
    # Pydantic's default mode turns "123" into 123 and true into 1; the wire contract
    # says integer, and Java refuses both. Strict fields keep the two sides aligned.
    payload = json.loads((FIXTURES / "run-start.valid.json").read_text(encoding="utf-8"))
    for coerced, value in (("run_id", "123"), ("run_id", True), ("run_id", None)):
        with pytest.raises(ValidationError):
            RunStartRequest.model_validate_json(json.dumps({**payload, coerced: value}))

    # The strict model still accepts the contract's own JSON representation.
    assert RunStartRequest.model_validate_json(json.dumps(payload)).run_id == payload["run_id"]


def test_a_status_timestamp_must_be_a_real_date() -> None:
    payload = json.loads((FIXTURES / "run-status.valid.json").read_text(encoding="utf-8"))

    with pytest.raises(ValidationError):
        RunStatusResponse.model_validate_json(
            json.dumps({**payload, "updated_at": "not-a-date"})
        )
