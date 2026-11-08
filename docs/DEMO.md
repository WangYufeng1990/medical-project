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
# backend: H2 profile, synthetic seed data; the agent is opt-in and needs the directional tokens
cd medical-server && \
  SPRING_PROFILES_ACTIVE=h2 \
  AGENT_ENABLED=true \
  AGENT_BASE_URL=http://localhost:8090 \
  AGENT_OUTBOUND_TOKEN=demo-java-to-python \
  AGENT_INBOUND_TOKEN=demo-python-to-java \
  mvn spring-boot:run

# agent: each side presents the token the other side expects
cd medical-agent && \
  MEDICAL_AGENT_INBOUND_TOKEN=demo-java-to-python \
  MEDICAL_AGENT_OUTBOUND_TOKEN=demo-python-to-java \
  MEDICAL_AGENT_JAVA_BASE_URL=http://localhost:8080 \
  MEDICAL_AGENT_PROVIDER=mock \
  PYTHONPATH=src .venv/bin/python -m uvicorn medical_agent.api.main:app --port 8090

# a live model instead of the mock: add MEDICAL_AGENT_PROVIDER=deepseek and
# MEDICAL_AGENT_DEEPSEEK_API_KEY=<key>; evaluation runs need hard caps or they refuse to start:
#   PYTHONPATH=src .venv/bin/python evals/run.py --provider deepseek --split held_out \
#     --max-requests 27 --max-usd 0.10 --price-per-request-usd 0.002 --report /tmp/run.json

# frontend (staff shell only; review actions are exercised through the API below)
cd medical-web && npm run dev   # http://localhost:5173
```

## Demo script (about six minutes, API for review actions)

There is **no review UI yet**: `medical-web` does not call `/api/v1/agent/reviews`, so the review
steps below are `curl` calls against the documented endpoints. Do not imply otherwise in a live demo.

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"doctor1","password":"<dev seed password>"}' | jq -r '.data.token')

# 1. a patient message produced a draft: list the review queue
curl -s $BASE/api/v1/agent/reviews -H "Authorization: Bearer $TOKEN" | jq

# 2. claim the run for review (the queue item shows who holds it)
curl -s -X POST $BASE/api/v1/agent/reviews/<runId>/claim -H "Authorization: Bearer $TOKEN" | jq

# 3a. approve, optionally with an edited final text - this is the only publication path
curl -s -X POST $BASE/api/v1/agent/reviews/<runId>/approve \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"finalText":"Edited reply for staff review."}' | jq

# 3b. or reject with a reason: nothing is published
curl -s -X POST $BASE/api/v1/agent/reviews/<runId>/reject \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"Wrong policy cited"}' | jq

# 4. verify the effect: the patient thread shows the published message only after approval,
#    and the audit log has the review decision
curl -s "$BASE/api/v1/messages/<patientId>" -H "Authorization: Bearer $TOKEN" | jq
curl -s "$BASE/api/v1/audit-logs?module=chat" -H "Authorization: Bearer $TOKEN" | jq
```

Additional things to show: a high-risk message routes to a human instead of a policy answer, and a
message that tries to instruct the system is flagged, routed to a human and triggers **zero** tool
calls (both are covered by the evaluation set and the worker tests).

## Evidence, by kind

| Figure | Value | Produced by | Kind |
|---|---|---|---|
| Java suite | 423 tests, 0 failures, 2 skipped | `mvn clean verify` | deterministic |
| Agent suite | 296 passed | `pytest` | deterministic |
| Dev evaluation | 300/300 cases | `evals/run.py` (canned answers) | deterministic |
| Dev per-class denominators | EMERGENCY 71, URGENT 28, ROUTINE 27 | the dev run's `per_class` block | deterministic |
| Held-out evaluation | 25/27, 19 requests, $0.038 recorded spend | `evals/run.py --provider deepseek --split held_out --max-requests 27 --max-usd 0.10 --price-per-request-usd 0.002 --report evals/reports/held-out-2026-11-06.json` | live model, measured once |
| Token usage | 238 prompt / 54 completion | a **separate** two-case live smoke (`--max-requests 2`), not the held-out run | live model |
| Cross-service trace | Java `agent.dispatch` span and five Python node spans under one trace, parent = Java span | `evals/verify_trace_link.py` over the canary probe | local OTLP probe, two processes |
| Canary absence | the synthetic marker appears in neither export | same script | local OTLP probe |

The historical held-out report predates per-class metrics and usage reporting, so it has `per_family`
numbers only — it does not contain the 71/28/27 denominators or the token counts, and those figures are
not read from it.

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
