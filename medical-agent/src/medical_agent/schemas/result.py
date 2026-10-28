"""The result contract (Round 57.1).

What Java needs in order to show a draft for review: the routing decision, the draft itself,
the citations it relies on and the validator's verdict. It is a separate schema from the start
request so the two can evolve independently.
"""

from typing import Annotated, Final, Literal

from pydantic import BaseModel, ConfigDict, Field

from medical_agent.schemas.classification import Intent, RiskLevel
from medical_agent.schemas.run import StrictPositiveInt

CONTRACT_VERSION: Final[Literal["v1"]] = "v1"


class CitationPayload(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    chunk_id: Annotated[str, Field(min_length=3, max_length=80,
                                   pattern=r"^[A-Za-z0-9_.:-]+$")]
    document_id: Annotated[str, Field(min_length=1, max_length=80,
                                      pattern=r"^[A-Za-z0-9_-]+$")]
    document_version: StrictPositiveInt
    title: Annotated[str, Field(min_length=1, max_length=200)]
    heading_path: Annotated[list[str], Field(max_length=8)]
    content_hash: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]


class ValidationPayload(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    ok: bool
    kind: Annotated[str, Field(min_length=1, max_length=32)]
    reasons: Annotated[list[str], Field(max_length=8)]
    rejected_reasons: Annotated[list[str], Field(max_length=8)]


class RunResultRequest(BaseModel):
    """The result a worker submits for one generation of one run."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    contract_version: Literal["v1"] = CONTRACT_VERSION
    run_id: StrictPositiveInt
    generation: StrictPositiveInt
    intent: Intent
    risk: RiskLevel
    requires_clinician: bool
    draft: Annotated[str, Field(min_length=1, max_length=1500)]
    citations: Annotated[list[CitationPayload], Field(max_length=8)]
    validation: ValidationPayload
