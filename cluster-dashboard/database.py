# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import sqlite3
from datetime import datetime, timezone
from typing import Optional

_SCHEMA = """
CREATE TABLE IF NOT EXISTS snapshots (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    ts             INTEGER NOT NULL,
    node_count     INTEGER DEFAULT 0,
    cpu_capacity   REAL    DEFAULT 0,
    cpu_allocatable REAL   DEFAULT 0,
    cpu_used       REAL,
    mem_capacity   INTEGER DEFAULT 0,
    mem_allocatable INTEGER DEFAULT 0,
    mem_used       INTEGER,
    gpu_capacity   INTEGER DEFAULT 0,
    gpu_allocatable INTEGER DEFAULT 0,
    gpu_used       REAL
);
CREATE INDEX IF NOT EXISTS idx_ts ON snapshots(ts);

CREATE TABLE IF NOT EXISTS run_snapshots (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_ts     INTEGER NOT NULL,
    node_name       TEXT    NOT NULL,
    run_id          INTEGER,
    run_name        TEXT,
    run_owner       TEXT,
    instance_type   TEXT,
    cpu_capacity    REAL    DEFAULT 0,
    cpu_allocatable REAL    DEFAULT 0,
    cpu_used        REAL,
    mem_capacity    INTEGER DEFAULT 0,
    mem_allocatable INTEGER DEFAULT 0,
    mem_used        INTEGER,
    gpu_capacity    INTEGER DEFAULT 0,
    gpu_allocatable INTEGER DEFAULT 0,
    gpu_used        REAL
);
CREATE INDEX IF NOT EXISTS idx_run_snap_ts ON run_snapshots(snapshot_ts);
"""

_RETENTION_DAYS = 7


def init_db(db_path: str) -> None:
    with sqlite3.connect(db_path) as conn:
        conn.executescript(_SCHEMA)
        conn.commit()


def insert_snapshot(db_path: str, snap: dict) -> None:
    with sqlite3.connect(db_path) as conn:
        conn.execute(
            """INSERT INTO snapshots
               (ts, node_count,
                cpu_capacity, cpu_allocatable, cpu_used,
                mem_capacity, mem_allocatable, mem_used,
                gpu_capacity, gpu_allocatable, gpu_used)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            (
                snap["ts"], snap["node_count"],
                snap["cpu_capacity"], snap["cpu_allocatable"], snap.get("cpu_used"),
                snap["mem_capacity"], snap["mem_allocatable"], snap.get("mem_used"),
                snap["gpu_capacity"], snap["gpu_allocatable"], snap.get("gpu_used"),
            ),
        )
        conn.commit()


def insert_run_snapshots(db_path: str, snapshot_ts: int, nodes: list) -> None:
    with sqlite3.connect(db_path) as conn:
        conn.executemany(
            """INSERT INTO run_snapshots
               (snapshot_ts, node_name, run_id, run_name, run_owner, instance_type,
                cpu_capacity, cpu_allocatable, cpu_used,
                mem_capacity, mem_allocatable, mem_used,
                gpu_capacity, gpu_allocatable, gpu_used)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            [
                (
                    snapshot_ts,
                    n["node_name"], n.get("run_id"), n.get("run_name"), n.get("run_owner"),
                    n.get("instance_type"),
                    n["cpu_capacity"], n["cpu_allocatable"], n.get("cpu_used"),
                    n["mem_capacity"], n["mem_allocatable"], n.get("mem_used"),
                    n["gpu_capacity"], n["gpu_allocatable"], n.get("gpu_used"),
                )
                for n in nodes
            ],
        )
        conn.commit()


def query_runs_for_ts(db_path: str, ts: int) -> dict:
    """Return run_snapshots for the snapshot whose ts is closest to `ts`."""
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        snap = conn.execute(
            "SELECT ts FROM snapshots ORDER BY ABS(ts - ?) LIMIT 1", (ts,)
        ).fetchone()
        if not snap:
            return {}
        actual_ts = snap["ts"]
        rows = conn.execute(
            """SELECT node_name, run_id, run_name, run_owner, instance_type,
                      cpu_capacity, cpu_allocatable, cpu_used,
                      mem_capacity, mem_allocatable, mem_used,
                      gpu_capacity, gpu_allocatable, gpu_used
               FROM run_snapshots
               WHERE snapshot_ts = ?
               ORDER BY CASE WHEN run_id IS NULL THEN 1 ELSE 0 END, node_name""",
            (actual_ts,),
        ).fetchall()
    return {"snapshot_ts": actual_ts, "nodes": [dict(r) for r in rows]}


def purge_old(db_path: str) -> None:
    cutoff = int(datetime.now(timezone.utc).timestamp()) - _RETENTION_DAYS * 86400
    with sqlite3.connect(db_path) as conn:
        conn.execute("DELETE FROM run_snapshots WHERE snapshot_ts < ?", (cutoff,))
        conn.execute("DELETE FROM snapshots WHERE ts < ?", (cutoff,))
        conn.commit()


def query_metrics(db_path: str, from_ts: int, to_ts: int) -> list:
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        rows = conn.execute(
            """SELECT ts, node_count,
                      cpu_capacity, cpu_allocatable, cpu_used,
                      mem_capacity, mem_allocatable, mem_used,
                      gpu_capacity, gpu_allocatable, gpu_used
               FROM snapshots
               WHERE ts >= ? AND ts <= ?
               ORDER BY ts ASC""",
            (from_ts, to_ts),
        ).fetchall()
    return [dict(r) for r in rows]


def get_latest(db_path: str) -> Optional[dict]:
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            "SELECT * FROM snapshots ORDER BY ts DESC LIMIT 1"
        ).fetchone()
    return dict(row) if row else None
