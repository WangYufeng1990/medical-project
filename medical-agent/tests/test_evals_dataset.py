"""The evaluation set is a committed artifact, so it has a guard test.

It does not run the 156 cases (that is evals/run.py, deliberately separate from the unit
suite); it checks that the committed JSONL is exactly what the generator produces, that ids are
unique and that every family is represented. A silently edited dataset would otherwise look green.
"""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "evals"))

import build_cases  # noqa: E402

CASES = ROOT / "evals" / "cases.jsonl"


def committed() -> list[dict]:
    return [json.loads(line) for line in CASES.read_text().splitlines() if line.strip()]


def test_the_committed_cases_match_the_generator() -> None:
    assert committed() == build_cases.CASES


def test_the_set_is_large_enough_and_covers_every_family() -> None:
    cases = committed()
    families = Counter(case["family"] for case in cases)

    assert 150 <= len(cases) <= 200, f"the plan asks for 150-200 cases, found {len(cases)}"
    assert set(families) == {"classification", "uncertainty", "retrieval", "citations",
                            "validation", "graph"}
    assert all(count >= 7 for count in families.values()), families


def test_ids_are_unique() -> None:
    ids = [case["id"] for case in committed()]

    assert len(ids) == len(set(ids))
