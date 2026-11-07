# Demo and evidence guide (Round 59.4)

This is the reproducibility sheet for the AI draft pipeline: what to run, what each number means,
and where each figure comes from. It is deliberately split by **evidence kind**, because a mocked
regression, a live-model measurement and a local tracing probe do not support the same claims.

## What the system does

1. A patient message is stored in Java and dispatched to the Python agent (`POST /internal/v1/runs`,
   bearer inbound token, per-run capability, one request id per attempt).
2. The agent validates the text, classifies intent/risk, retrieves approved policy chunks, optionally
   reads the run's own appointments through a read-only tool, drafts a reply, validates it against the
   evidence and submits the result with citations and a validation block.
3. Java stores the result as a review queue item. A human approves, edits or rejects it; only approval
   publishes anything, and the decision is audited.

## Run it (single instance, synthetic data)

```bash
# backend (H2 profile, synthetic seed data only)
cd medical-server && SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run

# agent (three values are required; the service refuses to start without them)
cd medical-agent && \
  MEDICAL_AGENT_INBOUND_TOKEN=<shared with app.agent.inbound-token> \
  MEDICAL_AGENT_OUTBOUND_TOKEN=<shared with app.agent.outbound-token> \
  MEDICAL_AGENT_JAVA_BASE_URL=http://localhost:8080 \
  MEDICAL_AGENT_PROVIDER=mock \
  PYTHONPATH=src .venv/bin/python -m uvicorn medical_agent.api.main:app --port 8090

# a live model instead of the mock: add MEDICAL_AGENT_PROVIDER=deepseek and
# MEDICAL_AGENT_DEEPSEEK_API_KEY=<key>; evaluation runs additionally need hard caps:
#   PYTHONPATH=src .venv/bin/python evals/run.py --provider deepseek --max-requests 27 \
#     --max-usd 0.10 --price-per-request-usd 0.002 --report /tmp/run.json

# frontend
cd medical-web && npm run dev   # http://localhost:5173, dev login documented in README
```

## Demo script (about six minutes)

1. Send a message as a patient; open the staff review queue and show the draft, citations, validation
   block and (when present) the appointment facts the tool returned.
2. Edit the draft and approve it; show the published message and the audit row.
3. Reject another draft with a reason; show that nothing is published.
4. Show a high-risk message routing to a human instead of a policy answer.
5. Show a message that tries to instruct the system: flagged, routed to a human, **zero** tool calls.

## Evidence, by kind

| Figure | Value | Produced by | Kind |
|---|---|---|---|
| Java suite | 423 tests, 0 failures, 2 skipped | `mvn clean verify` | deterministic |
| Agent suite | 296 passed | `pytest` | deterministic |
| Dev evaluation | 300/300 cases | `evals/run.py` (canned answers) | deterministic |
| Held-out evaluation | 25/27, 19 model requests, $0.038 recorded spend | `evals/run.py --provider deepseek --split held_out --report evals/reports/held-out-2026-11-06.json` | live model, measured once |
| Per-class denominators | EMERGENCY 71, URGENT 28, ROUTINE 27 | the same report (`per_class`) | live model |
| Token usage | 238 prompt / 54 completion on a two-case live run | `usage` block in the report | live model |
| Cross-service trace | Java `agent.dispatch` span and five Python node spans under one trace, parent = Java span | `evals/verify_trace_link.py` over the canary probe | local OTLP probe, two processes |
| Canary absence | the synthetic marker appears in neither export | same script | local OTLP probe |

The two held-out failures are `ho2-fever` and `ho2-pregnant`: both expected URGENT and received
EMERGENCY. That is over-triage relative to the label; it is not evidence of a safer clinical outcome.

## Boundaries this demo does **not** cross

- **Synthetic data only.** No real PHI, no BAA, no production provider terms; the DeepSeek calls in the
  held-out run carried synthetic text.
- **Single instance.** The audit hash chain is serialized for one JVM; multi-instance publication and
  notifications are out of scope, as is database-level audit serialization.
- **H2 and SQLite only.** Real MySQL/PostgreSQL migration, lease and concurrency behaviour is deferred.
- **Langfuse not verified.** Spans are exported as OTLP/HTTP JSON and were checked against a local
  collector; no Langfuse instance or credentials were involved.
- **Approval is the only publication path.** The AI never publishes; a wrong draft is a review burden,
  not a safety mechanism.
- **Retrieval is lexical over an approved corpus.** Dense/hybrid comparison, reranking and calibrated
  confidence are open items, not implemented claims.

## Trade-offs worth naming

- The graph is a small LangGraph with SQLite checkpoints: recovery is cheap to demonstrate and honest
  about limits, but it is a single-writer design.
- The tool surface is read-only and owns patient identity from the run, never from model output; that
  keeps the authorization story simple at the cost of not supporting model-requested writes.
- Classification is rule-first with the model as a judgement layer: deterministic safety rules are
  testable, and the rules win on escalation, which keeps injection and red-flag behaviour auditable.
- Cost reporting keeps measured tokens separate from a per-request price assumption, so a demo number
  cannot be mistaken for a bill.
