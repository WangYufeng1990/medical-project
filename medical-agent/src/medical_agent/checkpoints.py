"""Checkpointer selection and lifetime (Round 55.4, review).

Postgres is the deployment target; SQLite is what a developer machine and the tests can run
without a server. The factory is a context manager because both savers own a connection:
the Postgres one is opened by ``PostgresSaver.from_conn_string`` (its own context manager)
and the SQLite one by the ``sqlite3`` connection. Returning the saver alone would let the
connection close before ``setup()`` ever runs.
"""

import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver


@contextmanager
def open_checkpointer(url: str) -> Iterator[BaseCheckpointSaver[Any]]:
    if url.startswith(("postgres://", "postgresql://")):
        try:
            from langgraph.checkpoint.postgres import PostgresSaver
        except ImportError as exc:  # pragma: no cover - deployment dependency
            raise RuntimeError(
                "Postgres checkpoints need the 'langgraph-checkpoint-postgres' package "
                "and a working psycopg driver"
            ) from exc
        with PostgresSaver.from_conn_string(url) as postgres:
            postgres.setup()
            yield postgres
        return

    path = Path(url.removeprefix("sqlite://"))
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path, check_same_thread=False)
    try:
        from langgraph.checkpoint.sqlite import SqliteSaver

        sqlite = SqliteSaver(connection)
        sqlite.setup()
        yield sqlite
    finally:
        connection.close()
