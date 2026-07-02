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

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone

import requests
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
log = logging.getLogger(__name__)

# Master/edge node labels used by Cloud Pipeline to mark non-worker nodes
_MASTER_LABELS = {
    "node-role.kubernetes.io/master",
    "node-role.kubernetes.io/control-plane",
}
_MASTER_LABEL_VALUES = {
    "kubeadm.alpha.kubernetes.io/role": "master",
    "cloud-pipeline/role": "edge",
}


# ---------------------------------------------------------------------------
# Kubernetes resource parsing helpers
# ---------------------------------------------------------------------------

def parse_cpu(val) -> float:
    """'48' -> 48.0, '500m' -> 0.5"""
    if not val:
        return 0.0
    s = str(val).strip()
    return float(s[:-1]) / 1000.0 if s.endswith("m") else float(s)


def parse_memory(val) -> int:
    """'196627504Ki' -> bytes, '128Gi' -> bytes, raw int returned as-is."""
    if not val:
        return 0
    s = str(val).strip()
    for suffix, factor in [
        ("Ki", 1024), ("Mi", 1024 ** 2), ("Gi", 1024 ** 3), ("Ti", 1024 ** 4),
        ("K",  1000),  ("M",  1000 ** 2), ("G",  1000 ** 3), ("T",  1000 ** 4),
    ]:
        if s.endswith(suffix):
            return int(float(s[: -len(suffix)]) * factor)
    return int(s)


def _is_master(node: dict) -> bool:
    labels = node.get("labels") or {}
    if any(k in labels for k in _MASTER_LABELS):
        return True
    for k, v in _MASTER_LABEL_VALUES.items():
        if labels.get(k, "").lower() == v:
            return True
    return False


# ---------------------------------------------------------------------------
# Cloud Pipeline REST API client
# ---------------------------------------------------------------------------

class CPClient:
    """Minimal Cloud Pipeline REST API client (GET only, Bearer auth)."""

    def __init__(self, api_url: str, token: str):
        self._base = api_url.rstrip("/")
        self._session = requests.Session()
        self._session.headers.update({
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        })
        self._session.verify = False

    def get(self, path: str, **params):
        url = f"{self._base}/{path}"
        resp = self._session.get(url, params=params or None, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        if data.get("status") != "OK":
            raise RuntimeError(f"API error [{path}]: {data.get('message')}")
        return data.get("payload")


# ---------------------------------------------------------------------------
# Per-node usage helpers (best-effort; Heapster/cAdvisor must be running)
# ---------------------------------------------------------------------------

def _dt_range_strings(now: datetime):
    from_str = (now - timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S")
    to_str = now.strftime("%Y-%m-%d %H:%M:%S")
    return from_str, to_str


def _get_node_cpu_mem(client: CPClient, name: str, from_s: str, to_s: str):
    """Returns (cpu_cores_used, mem_bytes_used) or (None, None) on failure."""
    try:
        stats = client.get(f"cluster/node/{name}/usage", **{"from": from_s, "to": to_s})
        if not stats:
            return None, None
        # Take the last (most recent) data point in the response list
        last = stats[-1]
        cpu_obj = last.get("cpuUsage") or {}
        mem_obj = last.get("memoryUsage") or {}
        spec = last.get("containerSpec") or {}

        load  = cpu_obj.get("load")
        cores = spec.get("numberOfCores") or 0
        # CAdvisor returns load in absolute cores (delta_cpu_ns/period_ns);
        # older ES/Heapster backends return a 0..1 fraction that must be scaled.
        if load is not None and cores:
            cpu_used = load if load > 1.0 else load * cores
        else:
            cpu_used = None
        mem_used = mem_obj.get("usage") or None
        return cpu_used, mem_used
    except Exception as exc:
        log.debug("CPU/mem usage unavailable for node %s: %s", name, exc)
        return None, None


def _get_node_gpu_active(client: CPClient, name: str, from_s: str, to_s: str):
    """
    Returns the number of active GPUs (utilization > 0) or None on failure.
    Counts GPU indices from the most recent chart entry that has gpuDetails.
    """
    try:
        stats = client.get(
            f"cluster/node/{name}/usage/gpus",
            **{"from": from_s, "to": to_s, "granularity": "ALL", "squashCharts": "false"},
        )
        if not stats:
            return None
        for chart in reversed(stats.get("charts") or []):
            details = chart.get("gpuDetails")
            if details:
                return float(sum(
                    1 for gpu in details.values()
                    if (gpu.get("gpuUtilization") or {}).get("average", 0) > 0
                ))
        return None
    except Exception as exc:
        log.debug("GPU usage unavailable for node %s: %s", name, exc)
        return None


# ---------------------------------------------------------------------------
# Instance type → GPU count map
# ---------------------------------------------------------------------------

def _load_instance_gpu_map(client: CPClient) -> dict:
    """
    Fetch cluster/instance/allowed and return {instance_name: gpu_count}.
    Falls back to empty dict on error so collection can continue without GPU capacity.
    """
    try:
        payload = client.get("cluster/instance/allowed") or {}
        instances = payload.get("cluster.allowed.instance.types") or []
        result = {}
        for inst in instances:
            if inst.get("name"):
                gpu_device = inst.get("gpuDevice") or {}
                result[inst["name"]] = {
                    "count": int(inst.get("gpu") or 0),
                    "type":  gpu_device.get("name") or None,
                }
        return result
    except Exception as exc:
        log.warning("Could not load instance GPU map: %s", exc)
        return {}


# ---------------------------------------------------------------------------
# Instance type helpers
# ---------------------------------------------------------------------------

_INSTANCE_TYPE_LABELS = (
    "node.kubernetes.io/instance-type",
    "beta.kubernetes.io/instance-type",
)


def _instance_type(node: dict) -> str:
    """Extract instance type from pipelineRun.instance.nodeType or K8s labels."""
    run = node.get("pipelineRun") or {}
    instance = run.get("instance") or {}
    node_type = instance.get("nodeType") if isinstance(instance, dict) else None
    if node_type:
        return node_type
    labels = node.get("labels") or {}
    for label in _INSTANCE_TYPE_LABELS:
        if label in labels:
            return labels[label]
    return ""


def _run_info(node: dict) -> tuple:
    """Returns (run_id, run_name, run_owner) from pipelineRun, or (None, None, None)."""
    run = node.get("pipelineRun")
    if not run:
        return None, None, None
    run_id = run.get("id")
    name = run.get("pipelineName") or _docker_image_name(run.get("dockerImage", ""))
    owner = run.get("owner")
    return run_id, name, owner


def _docker_image_name(image: str) -> str:
    parts = image.split("/") if image else []
    return parts[-1] if parts else ""


# ---------------------------------------------------------------------------
# Main collection function
# ---------------------------------------------------------------------------

def _collect_node(client: CPClient, node: dict, from_s: str, to_s: str,
                  instance_gpu_map: dict) -> dict:
    """Collect all metrics for a single worker node. Called from a thread pool."""
    name  = node.get("name", "")
    cap   = node.get("capacity")    or {}
    alloc = node.get("allocatable") or {}

    n_cpu_cap   = parse_cpu(cap.get("cpu",   0))
    n_cpu_alloc = parse_cpu(alloc.get("cpu", 0))
    n_mem_cap   = parse_memory(cap.get("memory",   0))
    n_mem_alloc = parse_memory(alloc.get("memory", 0))

    itype = _instance_type(node)
    n_gpu_cap = (instance_gpu_map.get(itype) or {}).get("count", 0)

    cpu_u, mem_u = _get_node_cpu_mem(client, name, from_s, to_s)
    gpu_u = _get_node_gpu_active(client, name, from_s, to_s) if n_gpu_cap > 0 else None
    run_id, run_name, run_owner = _run_info(node)

    return {
        "node_name":       name,
        "run_id":          run_id,
        "run_name":        run_name,
        "run_owner":       run_owner,
        "instance_type":   itype,
        "cpu_capacity":    n_cpu_cap,
        "cpu_allocatable": n_cpu_alloc,
        "cpu_used":        cpu_u,
        "mem_capacity":    n_mem_cap,
        "mem_allocatable": n_mem_alloc,
        "mem_used":        int(mem_u) if mem_u is not None else None,
        "gpu_capacity":    n_gpu_cap,
        "gpu_allocatable": 0,
        "gpu_used":        gpu_u,
    }


def collect_snapshot(api_url: str, token: str, max_workers: int = 8):
    """
    Collect a cluster-wide utilization snapshot via the Cloud Pipeline REST API.

    Capacity/allocatable values come from cluster/node/loadAll (always available).
    CPU/memory/GPU utilization values come from the per-node usage endpoints backed
    by Elasticsearch/Heapster — they are None when monitoring is unavailable.

    Returns (snapshot_dict, node_list):
      - snapshot_dict  — aggregate cluster-wide metrics, ready for insert_snapshot()
      - node_list      — per-node details list, ready for insert_run_snapshots()
    """
    client = CPClient(api_url, token)

    nodes = client.get("cluster/node/loadAll") or []
    workers = [n for n in nodes if not _is_master(n)]

    now = datetime.now(timezone.utc)
    from_s, to_s = _dt_range_strings(now)

    instance_gpu_map = _load_instance_gpu_map(client)
    log.info("Loaded GPU map for %d instance types", len(instance_gpu_map))

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {
            pool.submit(_collect_node, client, node, from_s, to_s, instance_gpu_map): node
            for node in workers
        }
        node_list = []
        for future in as_completed(futures):
            try:
                node_list.append(future.result())
            except Exception:
                log.exception("Node collection failed for %s", futures[future].get("name"))

    cpu_cap = cpu_alloc = 0.0
    mem_cap = mem_alloc = 0
    gpu_cap = 0
    cpu_used_total = mem_used_total = 0.0
    gpu_used_total = 0.0
    usage_ok = gpu_usage_ok = 0

    gpu_breakdown = {}
    for r in node_list:
        cpu_cap   += r["cpu_capacity"];    cpu_alloc += r["cpu_allocatable"]
        mem_cap   += r["mem_capacity"];    mem_alloc += r["mem_allocatable"]
        gpu_cap   += r["gpu_capacity"]
        if r["cpu_used"] is not None:
            cpu_used_total += r["cpu_used"]
            usage_ok += 1
        if r["mem_used"] is not None:
            mem_used_total += r["mem_used"]
        if r["gpu_used"] is not None:
            gpu_used_total += r["gpu_used"]
            gpu_usage_ok += 1
        if r["gpu_capacity"] > 0:
            info = instance_gpu_map.get(r["instance_type"]) or {}
            gpu_type = info.get("type") or "Unknown"
            gpu_breakdown[gpu_type] = gpu_breakdown.get(gpu_type, 0) + r["gpu_capacity"]

    log.info(
        "Collected snapshot: %d workers, cpu_cap=%.1f, mem_cap=%.0f GiB, "
        "gpu_cap=%d, usage_ok=%d nodes",
        len(workers), cpu_cap, mem_cap / (1024 ** 3), gpu_cap, usage_ok,
    )

    snapshot = {
        "ts":              int(now.timestamp()),
        "node_count":      len(workers),
        "cpu_capacity":    cpu_cap,
        "cpu_allocatable": cpu_alloc,
        "cpu_used":        cpu_used_total if usage_ok > 0 else None,
        "mem_capacity":    mem_cap,
        "mem_allocatable": mem_alloc,
        "mem_used":        int(mem_used_total) if usage_ok > 0 else None,
        "gpu_capacity":    gpu_cap,
        "gpu_allocatable": 0,
        "gpu_used":        gpu_used_total if gpu_usage_ok > 0 else None,
        "gpu_breakdown":   gpu_breakdown or None,
    }
    return snapshot, node_list
