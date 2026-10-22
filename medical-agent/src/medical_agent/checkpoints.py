"""Checkpointer selection (Round 55.4).

Postgres is the deployment target; SQLite is what a developer machine and the tests can run
without a server. The URL decides, so the graph code does not know which one it has.
"""

import sqlite3
from pathlib import Path
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver


def open_checkpointer(url: str) -> BaseCheckpointSaver[Any]:
    if url.startswith(("postgres://", "postgresql://")):
        try:
            from langgraph.checkpoint.postgres import PostgresSaver
        except ImportError as exc:  # pragma: no cover - deployment dependency
            raise RuntimeError(
                "Postgres checkpoints need the 'langgraph-checkpoint-postgres' package "
                "and a working psycopg driver"
            ) from exc
        postgres = PostgresSaver.from_conn_string(url).__enter__()
        postgres.setup()
        return postgres

    path = Path(url.removeprefix("sqlite://"))
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path, check_same_thread=False)
    from langgraph.checkpoint.sqlite import SqliteSaver

    sqlite = SqliteSaver(connection)
    sqlite.setup()
    return sqlite
