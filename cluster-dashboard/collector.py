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
    """Returns mean number of active GPUs or None on failure."""
    try:
        stats = client.get(
            f"cluster/node/{name}/usage/gpus",
            **{"from": from_s, "to": to_s, "granularity": "GLOBAL"},
        )
        if not stats:
            return None
        last = stats[-1] if isinstance(stats, list) else stats
        gpu_obj = (last.get("gpuUsage") or {})
        active = gpu_obj.get("activeGpus") or {}
        return active.get("mean")
    except Exception as exc:
        log.debug("GPU usage unavailable for node %s: %s", name, exc)
        return None


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

def collect_snapshot(api_url: str, token: str):
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

    cpu_cap = cpu_alloc = 0.0
    mem_cap = mem_alloc = 0
    gpu_cap = gpu_alloc = 0
    cpu_used_total = mem_used_total = 0.0
    gpu_used_total = 0.0
    usage_ok = gpu_usage_ok = 0

    node_list = []

    for node in workers:
        name  = node.get("name", "")
        cap   = node.get("capacity")    or {}
        alloc = node.get("allocatable") or {}

        n_cpu_cap   = parse_cpu(cap.get("cpu",    0))
        n_cpu_alloc = parse_cpu(alloc.get("cpu",  0))
        n_mem_cap   = parse_memory(cap.get("memory",   0))
        n_mem_alloc = parse_memory(alloc.get("memory", 0))
        n_gpu_cap   = int(cap.get("nvidia.com/gpu")   or 0)
        n_gpu_alloc = int(alloc.get("nvidia.com/gpu") or 0)

        cpu_cap   += n_cpu_cap;   cpu_alloc += n_cpu_alloc
        mem_cap   += n_mem_cap;   mem_alloc += n_mem_alloc
        gpu_cap   += n_gpu_cap;   gpu_alloc += n_gpu_alloc

        cpu_u, mem_u = _get_node_cpu_mem(client, name, from_s, to_s)
        if cpu_u is not None:
            cpu_used_total += cpu_u
            usage_ok += 1
        if mem_u is not None:
            mem_used_total += mem_u

        gpu_u = None
        if n_gpu_cap > 0:
            gpu_u = _get_node_gpu_active(client, name, from_s, to_s)
            if gpu_u is not None:
                gpu_used_total += gpu_u
                gpu_usage_ok += 1

        run_id, run_name, run_owner = _run_info(node)
        node_list.append({
            "node_name":      name,
            "run_id":         run_id,
            "run_name":       run_name,
            "run_owner":      run_owner,
            "instance_type":  _instance_type(node),
            "cpu_capacity":   n_cpu_cap,
            "cpu_allocatable": n_cpu_alloc,
            "cpu_used":       cpu_u,
            "mem_capacity":   n_mem_cap,
            "mem_allocatable": n_mem_alloc,
            "mem_used":       int(mem_u) if mem_u is not None else None,
            "gpu_capacity":   n_gpu_cap,
            "gpu_allocatable": n_gpu_alloc,
            "gpu_used":       gpu_u,
        })

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
        "gpu_allocatable": gpu_alloc,
        "gpu_used":        gpu_used_total if gpu_usage_ok > 0 else None,
    }
    return snapshot, node_list
