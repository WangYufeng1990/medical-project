"""Regenerate contracts/agent/v1/*.schema.json from the Pydantic models.

    cd medical-agent && .venv/bin/python scripts/export_contracts.py
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from medical_agent.contracts import dumps, schema_documents

TARGET = Path(__file__).resolve().parents[2] / "contracts" / "agent" / "v1"


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, document in schema_documents().items():
        (TARGET / name).write_text(dumps(document), encoding="utf-8")
        print(f"wrote {TARGET / name}")


if __name__ == "__main__":
    main()
