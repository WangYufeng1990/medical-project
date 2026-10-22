"""Runs the graph in its own process. Used by test_checkpoints.py to prove that a resume
works after the process that crashed is gone, not just after a new object in the same one."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "src"))

from medical_agent.checkpoints import open_checkpointer
from medical_agent.java_client import CallCredentials
from medical_agent.providers.base import ProviderRequest, ProviderResponse
from medical_agent.schemas.run import RunStartRequest
from medical_agent.worker import AgentWorker


class CountingProvider:
    def __init__(self, calls_path: Path) -> None:
        self._path = calls_path

    async def invoke(self, request: ProviderRequest) -> ProviderResponse:
        with self._path.open("a", encoding="utf-8") as handle:
            handle.write("+")
        return ProviderResponse(text="mock", provider="mock", model="mock-1", latency_ms=0)


def submit(_state: dict[str, object]) -> None:
    if args.crash:
        raise RuntimeError("the process died while submitting")
    with Path(args.calls + ".submitted").open("a", encoding="utf-8") as handle:
        handle.write("+")


CREDENTIALS = CallCredentials(capability="cap", java_base_url="http://127.0.0.1:1",
                             outbound_token="out")


def main() -> int:
    with open_checkpointer(f"sqlite://{args.db}") as checkpointer:
        worker = AgentWorker(CountingProvider(Path(args.calls)), checkpointer, submit)
        _run(worker)
    return 0


def _run(worker: AgentWorker) -> None:
    if args.resume:
        worker.resume(args.run_id, CREDENTIALS)
    else:
        worker.process(
            RunStartRequest(
                run_id=args.run_id,
                message_id=9001,
                patient_id=100,
                conversation_revision=12,
                execution_generation=1,
                content="Can I reschedule my appointment?",
                request_id="req-0123456789",
            ),
            CREDENTIALS,
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True)
    parser.add_argument("--calls", required=True)
    parser.add_argument("--run-id", type=int, default=41)
    parser.add_argument("--crash", action="store_true")
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    sys.exit(main())
