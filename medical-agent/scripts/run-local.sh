#!/usr/bin/env bash
# Runs the service with medical-agent/.env exported. Local development only.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo "medical-agent/.env is missing; copy .env.example and fill it in" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a
exec .venv/bin/python -m uvicorn medical_agent.api.main:app --reload --port "${MEDICAL_AGENT_PORT:-8090}"
