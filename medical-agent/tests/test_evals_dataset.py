"""Guards for the committed evaluation set.

The set is a committed artifact, so these tests check that it is exactly what the generator
produces, that tampering or deletion would be noticed, and that importing the generator has no
file side effect (it used to rewrite the dataset on import, which made the comparison vacuous).
"""

from __future__ import annotations

import hashlib
import importlib
import importlib.util
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "evals"))

import build_cases  # noqa: E402

CASES = ROOT / "evals" / "cases.jsonl"


def read(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_the_committed_cases_match_the_generator() -> None:
    assert read(CASES) == build_cases.CASES


def test_a_tampered_or_shortened_file_no_longer_matches(tmp_path: Path) -> None:
    cases = read(CASES)
    tampered = tmp_path / "tampered.jsonl"
    tampered.write_text("\n".join(
        [json.dumps({**cases[0], "expect": {"bogus": True}}), *map(json.dumps, cases[1:])]
    ) + "\n")
    assert read(tampered) != build_cases.CASES, "a changed expectation must be detected"

    truncated = tmp_path / "truncated.jsonl"
    truncated.write_text("\n".join(map(json.dumps, cases[:-1])) + "\n")
    assert read(truncated) != build_cases.CASES, "a missing case must be detected"


def test_importing_the_generator_does_not_rewrite_the_file() -> None:
    before = digest(CASES)
    importlib.reload(build_cases)
    assert digest(CASES) == before, "importing must not touch the dataset"


def test_importing_a_copy_does_not_overwrite_a_different_dataset(tmp_path: Path) -> None:
    # The old implementation wrote the dataset at import time. A decoy file with different
    # content next to a copy of the generator is what locks that defect: a module-level write
    # would replace it, and comparing the real file's hash cannot see that.
    source = (ROOT / "evals" / "build_cases.py").read_text()
    copy = tmp_path / "build_cases.py"
    copy.write_text(source)
    decoy = tmp_path / "cases.jsonl"
    decoy.write_text('{"id": "decoy"}\n')

    spec = importlib.util.spec_from_file_location("decoy_build_cases", copy)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    assert decoy.read_text() == '{"id": "decoy"}\n', "importing must not overwrite a dataset"
    assert module.CASES, "the module still builds its cases in memory"


def test_the_set_is_large_enough_and_covers_every_family() -> None:
    cases = read(CASES)
    families = Counter(case["family"] for case in cases)

    assert 150 <= len(cases) <= 200, f"the plan asks for 150-200 cases, found {len(cases)}"
    assert set(families) == {"classification", "uncertainty", "retrieval", "citations",
                            "validation", "graph"}
    assert all(count >= 7 for count in families.values()), families


def test_every_case_declares_its_split() -> None:
    # The whole set is the development/regression split; a held-out split is an open item and
    # is not claimed by inventing a flag.
    assert {case["split"] for case in read(CASES)} == {"dev"}


def test_ids_are_unique() -> None:
    ids = [case["id"] for case in read(CASES)]

    assert len(ids) == len(set(ids))
