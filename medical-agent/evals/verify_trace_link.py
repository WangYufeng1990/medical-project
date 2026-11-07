"""Checks that one Java dispatch and the Python node spans belong to the same trace.

Round 59.2: the Java span ids are written by the opt-in live test, the Python spans by the JSONL
exporter, and the canary must appear in neither export. Run after a live probe:

    python evals/verify_trace_link.py --java /tmp/java-span.json \
        --python /tmp/probe-trace.jsonl --collector /tmp/otlp.jsonl --canary canary-trace-7f3a
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--python", type=Path, required=True)
    parser.add_argument("--collector", type=Path)
    parser.add_argument("--canary", default="canary-trace-7f3a")
    args = parser.parse_args()

    java = json.loads(args.java.read_text().splitlines()[-1])
    spans = [json.loads(line) for line in args.python.read_text().splitlines() if line.strip()]
    traces = {span["trace_id"] for span in spans}
    parents = {span["parent_span_id"] for span in spans}

    problems: list[str] = []
    if traces != {java["traceId"]}:
        problems.append(f"traces {traces} != java {java['traceId']}")
    if parents != {java["spanId"]}:
        problems.append(f"parents {parents} != java span {java['spanId']}")
    if args.canary and args.canary in args.python.read_text():
        problems.append("the Python export contains the canary")
    if args.collector is not None:
        raw = args.collector.read_bytes()
        if args.canary.encode() in raw:
            problems.append("the collector payload contains the canary")
        if not raw:
            problems.append("the collector received nothing")

    print(json.dumps({"java": java, "traces": sorted(traces), "parents": sorted(parents),
                      "spans": [span["name"] for span in spans], "problems": problems}, indent=2))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
