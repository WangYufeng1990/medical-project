"""ASGI entry point for `uvicorn medical_agent.api.main:app`."""

from __future__ import annotations

from medical_agent.api.app import create_app
from medical_agent.config import load_settings

app = create_app(load_settings())
