"""Round 55.4 — checkpoints survive the process that wrote them.

The graph tests cover resume inside one process. This one starts the work in a child
process that then dies, and resumes from a *different* process: what is restored is the
checkpoint on disk, not an object still in memory.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

HELPER = Path(__file__).parent / "helpers" / "resume_run.py"


def run_helper(*arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(HELPER), *arguments],
        capture_output=True,
        text=True,
        check=False,
        timeout=120,
    )


def test_a_different_process_resumes_and_does_not_repeat_the_classification(tmp_path: Path) -> None:
    database = tmp_path / "checkpoints.sqlite"
    calls = tmp_path / "calls.txt"

    crashed = run_helper("--db", str(database), "--calls", str(calls), "--crash")
    assert crashed.returncode != 0, "the first process must die at the submission"
    assert calls.read_text() == "+", "it classified once before dying"

    resumed = run_helper("--db", str(database), "--calls", str(calls), "--resume")
    assert resumed.returncode == 0, resumed.stderr
    assert calls.read_text() == "+", (
        "the resumed process must not classify again: " + calls.read_text()
    )
    submissions = tmp_path / "calls.txt.submitted"
    assert submissions.read_text() == "+", "the resumed process finished the submission"
