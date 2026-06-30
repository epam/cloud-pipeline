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
URL_PREFIX               URL path prefix, e.g. /cluster_dashboard (default: "")
"""

import logging
import os
import subprocess
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

from apscheduler.schedulers.background import BackgroundScheduler
from flask import Blueprint, Flask, abort, jsonify, request, send_from_directory

import database as db
from collector import CPClient, collect_snapshot

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CP_API_URL  = os.environ.get("CP_API_URL", "").rstrip("/")
CP_API_TOKEN = os.environ.get("CP_API_TOKEN", "")
COLLECT_INTERVAL_MINUTES = int(os.environ.get("COLLECT_INTERVAL_MINUTES", "60"))
URL_PREFIX   = os.environ.get("URL_PREFIX", "").rstrip("/")
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
    level=logging.DEBUG,
    format="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
)
log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Flask app
# ---------------------------------------------------------------------------

app = Flask(__name__, static_folder="static")
bp  = Blueprint("dashboard", __name__)


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

@bp.route("/")
def dashboard():
    return send_from_directory("static", "index.html")


@bp.route("/api/metrics")
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


@bp.route("/api/status")
def api_status():
    latest = db.get_latest(DB_PATH)
    return jsonify(latest or {})


@bp.route("/api/runs")
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


@bp.route("/api/health")
def api_health():
    return jsonify({
        "status": "ok",
        "collect_interval_minutes": COLLECT_INTERVAL_MINUTES,
        "api_configured": bool(CP_API_URL and CP_API_TOKEN),
        "ui_url": CP_UI_URL,
    })


@bp.route("/api/run/<int:run_id>/details")
def api_run_details(run_id):
    result = {
        "run_id":               run_id,
        "processes":            None,
        "gpus":                 None,
        "last_paused":          None,
        "last_resumed":         None,
        "last_ssh_access":      None,
        "last_endpoint_access": None,
    }
    if CP_API_URL and CP_API_TOKEN:
        try:
            run = CPClient(CP_API_URL, CP_API_TOKEN).get(f"run/{run_id}")
            if run:
                result.update(_extract_run_activity(run))
        except Exception:
            log.exception("Failed to fetch run details for run %d", run_id)
    result["processes"] = _get_run_processes(run_id)
    result["gpus"]      = _get_run_gpus(run_id)
    return jsonify(result)


def _extract_run_activity(run: dict) -> dict:
    statuses = sorted(
        run.get("runStatuses") or [],
        key=lambda s: s.get("timestamp") or "",
    )
    last_paused = last_resumed = None
    prev_paused = False
    for s in statuses:
        st = (s.get("status") or "").upper()
        ts = s.get("timestamp")
        if st == "PAUSED":
            last_paused = ts
            prev_paused = True
        elif st == "RUNNING":
            if prev_paused:
                last_resumed = ts
            prev_paused = False
        else:
            prev_paused = False
    return {"last_paused": last_paused, "last_resumed": last_resumed}


def _get_run_processes(run_id: int):
    try:
        r = subprocess.run(
            ["pipe", "ssh", str(run_id), "top -bn2 -d 1"],
            capture_output=True, text=True, timeout=25,
        )
        if r.returncode == 0 and r.stdout.strip():
            return _parse_top_output(r.stdout)
        log.debug("pipe ssh exited %d for run %d: %s", r.returncode, run_id, r.stderr[:200])
    except FileNotFoundError:
        log.debug("pipe CLI not found — process list unavailable")
    except subprocess.TimeoutExpired:
        log.warning("pipe ssh timed out for run %d", run_id)
    except Exception as exc:
        log.debug("pipe ssh failed for run %d: %s", run_id, exc)
    return None


def _parse_top_output(text: str) -> list:
    lines = text.splitlines()
    # Find the last PID/USER header — second iteration gives 1-second delta %CPU
    header_idx = None
    for i, line in enumerate(lines):
        if "PID" in line and "USER" in line and "%CPU" in line:
            header_idx = i
    if header_idx is None:
        return []
    rows = []
    for line in lines[header_idx + 1:]:
        parts = line.split(None, 11)
        if len(parts) >= 10 and parts[0].isdigit():
            rows.append({
                "user":    parts[1],
                "pid":     parts[0],
                "cpu":     parts[8],
                "mem":     parts[9],
                "command": parts[11] if len(parts) > 11 else "",
            })
    rows.sort(key=lambda r: float(r["cpu"]) if r["cpu"].replace(".", "", 1).isdigit() else 0, reverse=True)
    return rows


def _get_run_gpus(run_id: int):
    try:
        r = subprocess.run(
            ["pipe", "ssh", str(run_id),
             "nvidia-smi --query-gpu=index,name,utilization.gpu,memory.used,memory.total"
             " --format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=20,
        )
        if r.returncode == 0 and r.stdout.strip():
            return _parse_nvidia_smi_output(r.stdout)
        log.debug("nvidia-smi exited %d for run %d: %s", r.returncode, run_id, r.stderr[:200])
    except FileNotFoundError:
        log.debug("pipe CLI not found — GPU list unavailable")
    except subprocess.TimeoutExpired:
        log.warning("pipe ssh (nvidia-smi) timed out for run %d", run_id)
    except Exception as exc:
        log.debug("pipe ssh (nvidia-smi) failed for run %d: %s", run_id, exc)
    return None


def _parse_nvidia_smi_output(text: str) -> list:
    rows = []
    for line in text.strip().splitlines():
        parts = [p.strip() for p in line.split(",")]
        if len(parts) >= 5:
            rows.append({
                "id":        parts[0],
                "name":      parts[1],
                "gpu_util":  parts[2],
                "mem_used":  parts[3],
                "mem_total": parts[4],
            })
    return rows


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
# Blueprint registration
# ---------------------------------------------------------------------------

app.register_blueprint(bp, url_prefix=URL_PREFIX or None)

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
