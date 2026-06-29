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

"""
Cluster Utilization Dashboard — Flask backend.

Environment variables
---------------------
CP_API_URL               Cloud Pipeline REST API base URL, e.g.
                         https://cp.example.com/pipeline/restapi
                         (same value used by the `pipe` CLI tool)
CP_API_TOKEN             Bearer token for API authentication
COLLECT_INTERVAL_MINUTES Collection period in minutes (default: 60)
DB_PATH                  Path to the SQLite database file
                         (default: <app-dir>/data/metrics.db)
PORT                     HTTP port (default: 5000)
"""

import logging
import os
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask, abort, jsonify, request, send_from_directory

import database as db
from collector import collect_snapshot

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CP_API_URL  = os.environ.get("CP_API_URL", "").rstrip("/")
CP_API_TOKEN = os.environ.get("CP_API_TOKEN", "")
COLLECT_INTERVAL_MINUTES = int(os.environ.get("COLLECT_INTERVAL_MINUTES", "60"))
_APP_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH  = os.environ.get("DB_PATH", os.path.join(_APP_DIR, "data", "metrics.db"))
PORT     = int(os.environ.get("PORT", "5000"))

_parsed = urlparse(CP_API_URL) if CP_API_URL else None
_derived_ui_url = f"{_parsed.scheme}://{_parsed.netloc}" if _parsed else ""
CP_UI_URL = os.environ.get("CP_UI_URL", _derived_ui_url)

_MAX_RANGE_DAYS = 7

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
)
log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Flask app
# ---------------------------------------------------------------------------

app = Flask(__name__, static_folder="static")


# ---------------------------------------------------------------------------
# Scheduled collection
# ---------------------------------------------------------------------------

def _run_collection() -> None:
    if not CP_API_URL or not CP_API_TOKEN:
        log.warning("CP_API_URL or CP_API_TOKEN not set — skipping collection")
        return
    try:
        snap, nodes = collect_snapshot(CP_API_URL, CP_API_TOKEN)
        db.insert_snapshot(DB_PATH, snap)
        db.insert_run_snapshots(DB_PATH, snap["ts"], nodes)
        db.purge_old(DB_PATH)
    except Exception:
        log.exception("Snapshot collection failed")


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/")
def dashboard():
    return send_from_directory("static", "index.html")


@app.route("/api/metrics")
def api_metrics():
    now = datetime.now(timezone.utc)
    from_str = request.args.get("from")
    to_str   = request.args.get("to")

    try:
        from_dt = (
            datetime.fromisoformat(from_str.replace("Z", "+00:00"))
            if from_str else now - timedelta(hours=24)
        )
        to_dt = (
            datetime.fromisoformat(to_str.replace("Z", "+00:00"))
            if to_str else now
        )
    except ValueError:
        abort(400, "Invalid ISO-8601 date in 'from' or 'to' parameter")

    # Enforce maximum range
    if (to_dt - from_dt).total_seconds() > _MAX_RANGE_DAYS * 86400:
        from_dt = to_dt - timedelta(days=_MAX_RANGE_DAYS)

    rows = db.query_metrics(DB_PATH, int(from_dt.timestamp()), int(to_dt.timestamp()))
    return jsonify(rows)


@app.route("/api/status")
def api_status():
    latest = db.get_latest(DB_PATH)
    return jsonify(latest or {})


@app.route("/api/runs")
def api_runs():
    ts_str = request.args.get("ts")
    if not ts_str:
        abort(400, "Missing 'ts' parameter")
    try:
        ts = int(float(ts_str))
    except ValueError:
        abort(400, "Invalid 'ts' — expected Unix seconds")
    result = db.query_runs_for_ts(DB_PATH, ts)
    return jsonify(result)


@app.route("/api/health")
def api_health():
    return jsonify({
        "status": "ok",
        "collect_interval_minutes": COLLECT_INTERVAL_MINUTES,
        "api_configured": bool(CP_API_URL and CP_API_TOKEN),
        "ui_url": CP_UI_URL,
    })


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    db.init_db(DB_PATH)

    scheduler = BackgroundScheduler(daemon=True)
    scheduler.add_job(
        _run_collection,
        "interval",
        minutes=COLLECT_INTERVAL_MINUTES,
        id="collect",
        max_instances=1,
        coalesce=True,
    )
    scheduler.start()
    log.info(
        "Scheduler started — collecting every %d minute(s)", COLLECT_INTERVAL_MINUTES
    )

    # Collect once immediately so the dashboard has data on first load
    _run_collection()

    app.run(host="0.0.0.0", port=PORT, use_reloader=False)
