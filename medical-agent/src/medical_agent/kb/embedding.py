"""A deterministic embedding baseline (Round 56.2).

No model and no network: tokens are hashed into a fixed number of dimensions. It is enough to
build and test the ingestion and retrieval plumbing, it is exactly reproducible, and it is
labelled as a baseline — a real embedding model replaces it behind the same function when the
evaluation in 56.3 compares baselines.
"""

from __future__ import annotations

import hashlib
import math
import re

EMBEDDING_VERSION = "hash64-v1"
DIMENSIONS = 64
_TOKEN = re.compile(r"[a-z0-9']+")


def embed(text: str) -> list[float]:
    vector = [0.0] * DIMENSIONS
    for token in _TOKEN.findall(text.lower()):
        digest = hashlib.sha256(token.encode()).digest()
        bucket = int.from_bytes(digest[:4], "big") % DIMENSIONS
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[bucket] += sign
    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        return vector
    return [value / norm for value in vector]
