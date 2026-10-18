#!/usr/bin/env python3
"""Execute numbered FalkorDB/Cypher .txt files from a dataset directory."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
from pathlib import Path

from falkordb import FalkorDB


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATA_DIR = ROOT / "data" / "cypher"


def project_env() -> dict[str, str]:
    """Read simple KEY=VALUE entries from .env; process environment wins."""
    values: dict[str, str] = {}
    env_file = ROOT / ".env"
    if env_file.exists():
        for raw_line in env_file.read_text(encoding="utf-8-sig").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    values.update(os.environ)
    return values


def numbered_order(path: Path) -> tuple[int, str]:
    match = re.match(r"^(\d+)", path.name)
    return (int(match.group(1)) if match else 9999, path.name.lower())


def load_queries(data_dir: Path, graph) -> int:
    if not data_dir.is_dir():
        raise FileNotFoundError(
            f"Dataset directory not found: {data_dir}\n"
            f"Pass the folder containing the Cypher .txt files, for example:\n"
            f'  python scripts/load_demo_data.py "C:\\path\\to\\cust-rel"'
        )

    files = sorted(data_dir.glob("*.txt"), key=numbered_order)
    if not files:
        raise FileNotFoundError(f"No .txt files found in {data_dir}")

    loaded_hashes: set[str] = set()
    loaded_count = 0
    for path in files:
        query = path.read_text(encoding="utf-8-sig").strip()
        if not query:
            print(f"Skipping empty file: {path.name}")
            continue
        if not query.upper().startswith("UNWIND "):
            print(f"Skipping non-Cypher text file: {path.name}")
            continue

        digest = hashlib.sha256(query.encode("utf-8")).hexdigest()
        if digest in loaded_hashes:
            print(f"Skipping duplicate query: {path.name}")
            continue

        print(f"Loading {path.name}...", flush=True)
        graph.query(query)
        loaded_hashes.add(digest)
        loaded_count += 1

    if loaded_count == 0:
        raise ValueError(f"No Cypher UNWIND files were loaded from {data_dir}")
    return loaded_count


def main() -> None:
    config = project_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "data_dir",
        nargs="?",
        default=config.get("DEMO_DATA_DIR", str(DEFAULT_DATA_DIR)),
        help="directory containing numbered Cypher .txt files",
    )
    args = parser.parse_args()

    data_dir = Path(args.data_dir).expanduser().resolve()
    url = config.get("FALKORDB_URL", "redis://localhost:6379")
    graph_name = config.get("FALKORDB_GRAPH", "customer_relationships")
    client = FalkorDB.from_url(url)
    try:
        graph = client.select_graph(graph_name)
        print(f"Loading Cypher files from {data_dir} into FalkorDB graph '{graph_name}'...")
        count = load_queries(data_dir, graph)
        print(f"Done. Loaded {count} Cypher dataset file(s) into '{graph_name}'.")
    finally:
        client.close()


if __name__ == "__main__":
    main()
