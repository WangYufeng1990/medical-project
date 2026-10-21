"""Shared fixtures: a clean MEDICAL_AGENT_* environment for every test."""

from __future__ import annotations

import os

import pytest


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    """No test inherits another test's configuration, and none inherits the shell's."""
    for name in list(os.environ):
        if name.startswith("MEDICAL_AGENT_"):
            monkeypatch.delenv(name, raising=False)
