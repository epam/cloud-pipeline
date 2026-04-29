#!/usr/bin/env python3

import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import date, datetime, time, timedelta, timezone
from typing import DefaultDict, Dict, FrozenSet, List, Optional, Set, Tuple


def _tuple_from_csv_env(var_name: str, default: Tuple[str, ...]) -> Tuple[str, ...]:
    raw = os.getenv(var_name)
    if raw is None or not str(raw).strip():
        return default
    parts = tuple(s.strip() for s in str(raw).split(",") if s.strip())
    return parts if parts else default


DEFAULT_DATE_FROM = os.getenv('CP_USAGE_METRICS_DEFAULT_DATE_FROM', '2026-01-01')
RESERVATION_PREFERENCE = os.getenv('CP_USAGE_METRICS_RESERVATION_PREFERENCE', 'launch.reservation.parameters')
WORKLOAD_KEYS = _tuple_from_csv_env("CP_USAGE_METRICS_WORKLOAD_KEYS", ("gpu-heavy", "cpu-heavy", "memory-heavy",
                                                                       "general-purpose"))
GPU_FAMILY_PREFIXES = _tuple_from_csv_env("CP_USAGE_METRICS_GPU_FAMILY_PREFIXES", ("g", "p"))
EXCLUDED_USER_GROUPS_LOWER: FrozenSet[str] = frozenset(
    s.lower()
    for s in _tuple_from_csv_env(
        "CP_USAGE_METRICS_EXCLUDE_USER_GROUPS",
        ("ROLE_ADMIN",),
    )
    if s
)
INTERACTIVE_CMD = "sleep infinity"
CAP_BLOCK_TAG = "Capacity Block"
GIB = 1073741824
FLOAT_DECIMAL = 6
PIPELINE_INCLUDE_CONFIG_NAME = os.getenv('CP_USAGE_METRICS_PIPELINE_INCLUDE_CONFIG_NAME', True)


class ReportSchema:
    DATE_FMT_SHORT = "%Y-%m-%d"

    RESOURCE_KEYS = {
        "CPU": "cpu",
        "GPU": "nvidia.com/gpu",
        "RAM (GiB)": "memory",
    }

    RUN_FETCH_STATUSES = [
        "SUCCESS",
        "FAILURE",
        "STOPPED",
        "RUNNING",
        "PAUSED",
        "PAUSING",
        "RESUMING",
    ]

    @classmethod
    def expected_header(cls) -> str:
        return (
            "date\t"
            "total_active_users\t"
            "new_users_onboarded\t"
            "total_jobs_executed\t"
            "compute_cpu_hours\tcompute_gpu_hours\t"
            "capacity_cpu_total\tcapacity_gpu_total\tcapacity_memory_total_gib\t"
            "capacity_cpu_avg\tcapacity_gpu_avg\tcapacity_memory_avg_gib\t"
            "capacity_cpu_max\tcapacity_gpu_max\tcapacity_memory_max_gib\t"
            "cb_hours\toutside_hours\tcb_outside_total_hours\t"
            "cb_runs\toutside_runs\t"
            "top1_pipeline_name\ttop1_pipeline_hours\ttop1_pipeline_cost\ttop1_pipeline_runs\t"
            "top2_pipeline_name\ttop2_pipeline_hours\ttop2_pipeline_cost\ttop2_pipeline_runs\t"
            "top3_pipeline_name\ttop3_pipeline_hours\ttop3_pipeline_cost\ttop3_pipeline_runs\t"
            "top1_docker_name\ttop1_docker_hours\ttop1_docker_cost\ttop1_docker_runs\t"
            "top2_docker_name\ttop2_docker_hours\ttop2_docker_cost\ttop2_docker_runs\t"
            "top3_docker_name\ttop3_docker_hours\ttop3_docker_cost\ttop3_docker_runs\t"
            "jobs_interactive\tjobs_batch\t"
            "wc_gpu_heavy\twc_cpu_heavy\twc_memory_heavy\twc_general_purpose\t"
            "dur_gpu_heavy_hours\tdur_cpu_heavy_hours\tdur_memory_heavy_hours\tdur_general_purpose_hours\t"
            "failures\tstopped"
        )


def default_report_day_utc() -> date:
    """Yesterday in UTC. Today is excluded as an incomplete calendar day."""
    return datetime.now(timezone.utc).date() - timedelta(days=1)


def new_users_onboarded_by_calendar_day(
    users: List[dict], report_start_day: date, report_end_day: date
) -> Dict[date, int]:
    """Counts users whose registrationDate falls on each calendar day."""
    counts: DefaultDict[date, int] = defaultdict(int)
    for user in users:
        if not isinstance(user, dict):
            continue
        registered_at = RunTimeline.parse_run_datetime(user.get("registrationDate"))
        if registered_at is None:
            continue
        registered_at = RunTimeline.normalize_naive_utc(registered_at)
        registration_calendar_day = registered_at.date()
        if report_start_day <= registration_calendar_day <= report_end_day:
            counts[registration_calendar_day] += 1
    return dict(counts)


def owners_matching_excluded_user_groups(users: List[dict], excluded_lower: FrozenSet[str]) -> FrozenSet[str]:
    """ Usernames of users who have any role name or group string in excluded."""
    if not excluded_lower:
        return frozenset()
    excluded_users: Set[str] = set()
    for user in users:
        if not isinstance(user, dict):
            continue
        username = user.get("userName")
        if username is None:
            username = user.get("name")
        if username is None or not str(username).strip():
            continue
        matched = False
        for role in user.get("roles") or []:
            if not isinstance(role, dict):
                continue
            role_name = role.get("name")
            if role_name is not None and str(role_name).strip().lower() in excluded_lower:
                matched = True
                break
        if not matched:
            for g in user.get("groups") or []:
                if g is not None and str(g).strip().lower() in excluded_lower:
                    matched = True
                    break
        if matched:
            excluded_users.add(str(username).strip().lower())
    return frozenset(excluded_users)


class RunTimeline:
    @staticmethod
    def parse_run_datetime(value) -> Optional[datetime]:
        if value is None:
            return None
        s = str(value).strip().replace("Z", "")
        if not s:
            return None
        s = s.replace("T", " ", 1)
        parts = s.split()
        if not parts:
            return None
        try:
            day = datetime.strptime(parts[0], "%Y-%m-%d").date()
            if len(parts) == 1:
                return datetime.combine(day, datetime.min.time())
            tpart = parts[1]
            if "." in tpart:
                return datetime.strptime(f"{parts[0]} {tpart}", "%Y-%m-%d %H:%M:%S.%f")
            return datetime.strptime(f"{parts[0]} {tpart}", "%Y-%m-%d %H:%M:%S")
        except ValueError:
            return None

    @staticmethod
    def normalize_naive_utc(dt: datetime) -> datetime:
        if dt.tzinfo is not None:
            return dt.astimezone(timezone.utc).replace(tzinfo=None)
        return dt

    @staticmethod
    def day_window(calendar_day: date) -> Tuple[datetime, datetime]:
        day_start = datetime.combine(calendar_day, time.min)
        return day_start, day_start + timedelta(days=1)

    @staticmethod
    def overlap_seconds(
        interval_start: datetime,
        interval_end: datetime,
        window_start: datetime,
        window_end_exclusive: datetime,
    ) -> float:
        overlap_start = max(interval_start, window_start)
        overlap_end = min(interval_end, window_end_exclusive)
        if overlap_end <= overlap_start:
            return 0.0
        return (overlap_end - overlap_start).total_seconds()

    @staticmethod
    def effective_run_end(run: dict, fallback_end: datetime) -> datetime:
        end = RunTimeline.parse_run_datetime(run.get("endDate"))
        if end is not None:
            return RunTimeline.normalize_naive_utc(end)
        return RunTimeline.normalize_naive_utc(fallback_end)

    @staticmethod
    def overlapping_day_range(
        run_start: datetime,
        run_end: datetime,
        report_start_day: date,
        report_end_day: date,
    ) -> Optional[Tuple[date, date]]:
        start = RunTimeline.normalize_naive_utc(run_start)
        end = RunTimeline.normalize_naive_utc(run_end)
        report_window_start = datetime.combine(report_start_day, time.min)
        report_window_end_exclusive = datetime.combine(report_end_day, time.min) + timedelta(days=1)
        if not (start < report_window_end_exclusive and end > report_window_start):
            return None
        overlap_first_day = max(report_start_day, start.date())
        overlap_last_day = min(report_end_day, (end - timedelta(microseconds=1)).date())
        if overlap_first_day > overlap_last_day:
            return None
        return overlap_first_day, overlap_last_day

    @staticmethod
    def paused_seconds_in_day_window(
        run: dict,
        run_start: datetime,
        effective_run_end: datetime,
        day_window_start: datetime,
        day_window_end_exclusive: datetime,
    ) -> float:
        run_statuses = run.get("runStatuses") or []
        if not run_statuses:
            return 0.0

        timeline_start = RunTimeline.parse_run_datetime(run.get("instanceStartDate"))
        if timeline_start is None:
            timeline_start = run_start
        timeline_start = RunTimeline.normalize_naive_utc(timeline_start)
        timeline_end = effective_run_end
        if timeline_end <= timeline_start:
            return 0.0

        events: List[Tuple[datetime, str]] = [
            (timeline_start, "RUNNING"),
            (timeline_end, "STOPPED"),
        ]
        for status_record in run_statuses:
            status_raw = status_record.get("status")
            if status_raw is None:
                continue
            status_label = status_raw if isinstance(status_raw, str) else str(status_raw)
            event_time = RunTimeline.parse_run_datetime(status_record.get("timestamp"))
            if event_time is None:
                continue
            events.append((RunTimeline.normalize_naive_utc(event_time), status_label))

        events.sort(key=lambda x: (x[0], x[1]))

        total_paused_seconds = 0.0
        for segment_index in range(len(events) - 1):
            segment_start, status_at_segment_start = events[segment_index]
            segment_end = events[segment_index + 1][0]
            if segment_end <= segment_start:
                continue
            if status_at_segment_start != "PAUSED":
                continue
            paused_interval_start = max(segment_start, run_start)
            paused_interval_end = min(segment_end, effective_run_end)
            if paused_interval_end <= paused_interval_start:
                continue
            total_paused_seconds += RunTimeline.overlap_seconds(
                paused_interval_start,
                paused_interval_end,
                day_window_start,
                day_window_end_exclusive,
            )
        return total_paused_seconds


class RunFields:
    @staticmethod
    def instance_family(node_type: str) -> str:
        if not node_type:
            return ""
        return node_type.split(".", 1)[0].strip().lower()

    @classmethod
    def is_gpu_instance(cls, node_type: str) -> bool:
        family = cls.instance_family(node_type)
        return any(family.startswith(p) for p in GPU_FAMILY_PREFIXES)

    @staticmethod
    def workload_class(family: str) -> str:
        if not family:
            return "unknown"
        f = family.lower()
        if f.startswith("g") or f.startswith("p2") or f.startswith("p5"):
            return "gpu-heavy"
        if f.startswith("c5"):
            return "cpu-heavy"
        if f.startswith("r5"):
            return "memory-heavy"
        if f.startswith("m5"):
            return "general-purpose"
        return "other"

    @classmethod
    def run_workload_key(cls, run: dict) -> str:
        inst = run.get("instance") or {}
        node_type = inst.get("nodeType") or ""
        family = node_type.split(".")[0] if node_type else ""
        return cls.workload_class(family)

    @classmethod
    def run_matches_excluded_owner(cls, run: dict, excluded_owners_lower: FrozenSet[str]) -> bool:
        if not excluded_owners_lower:
            return False
        owner = run.get("owner")
        if owner is None:
            return False
        s = str(owner).strip().lower()
        return bool(s and s in excluded_owners_lower)

    @staticmethod
    def run_status_str(run: dict) -> str:
        status = run.get("status")
        if status is None:
            return ""
        return status if isinstance(status, str) else str(status)

    @staticmethod
    def short_image(docker_image: str) -> str:
        if not docker_image:
            return "unknown"
        parts = docker_image.rsplit("/", 1)
        return parts[-1] if parts else docker_image

    @classmethod
    def pipeline_name_field(cls, run: dict) -> Optional[str]:
        raw_name = run.get("pipelineName")
        if raw_name is None:
            return None
        name = str(raw_name).strip()
        if not name:
            return None
        if PIPELINE_INCLUDE_CONFIG_NAME:
            raw_cfg = run.get("configName")
            cfg = str(raw_cfg).strip() if raw_cfg is not None else ""
            if cfg:
                return f"{name} | {cfg}"
        return name


class PipelineRestClient:
    def __init__(self, api_base: str, token: str) -> None:
        self.api_base = api_base.rstrip("/")
        self.token = token
        self._ssl = ssl.create_default_context()
        self._ssl.check_hostname = False
        self._ssl.verify_mode = ssl.CERT_NONE

    def post_json(self, path: str, body: dict) -> dict:
        url = f"{self.api_base}{path}"
        data = json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=data,
            method="POST",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json; charset=UTF-8",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, context=self._ssl, timeout=300) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            print(e.read().decode("utf-8", errors="replace"), file=sys.stderr)
            raise SystemExit(1) from e

    def get_json(self, path: str) -> dict:
        url = f"{self.api_base}{path}"
        request = urllib.request.Request(
            url,
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, context=self._ssl, timeout=120) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            print(e.read().decode("utf-8", errors="replace"), file=sys.stderr)
            raise SystemExit(1) from e

    def load_users(self) -> List[dict]:
        response = self.get_json("/users?activity=false&quotas=false")
        payload = response.get("payload")
        if not isinstance(payload, list):
            raise SystemExit(f"Unexpected /users response (expected list): {response!r}")
        return payload

    def fetch_all_runs(
        self,
        start_date_from: str,
        page_size: int,
        statuses: List[str],
        end_date_to: Optional[str] = None,
    ) -> List[dict]:
        runs: List[dict] = []
        page = 1
        while True:
            body: dict = {
                "statuses": statuses,
                "startDateFrom": start_date_from,
                "userModified": True,
                "eagerGrouping": False,
                "page": page,
                "pageSize": page_size,
            }
            if end_date_to is not None:
                body["endDateTo"] = end_date_to
            payload = self.post_json("/run/filter", body).get("payload") or {}
            elements = payload.get("elements") or []
            if not elements:
                break
            runs.extend(elements)
            total = int(payload.get("totalCount") or 0)
            if page * page_size >= total:
                break
            page += 1
        return runs

    def capacity_block_instance_types(self) -> Set[str]:
        response = self.get_json(f"/preferences/{RESERVATION_PREFERENCE}")
        raw = (response.get("payload") or {}).get("value") or "{}"
        try:
            config = json.loads(raw)
        except json.JSONDecodeError:
            return set()
        return {
            inst_type
            for inst_type, opts in config.items()
            if isinstance(opts, dict) and opts.get("tag") == CAP_BLOCK_TAG
        }

    def active_pool_ids(self) -> Dict[int, str]:
        response = self.get_json("/cluster/pool?loadStatus=true")
        pools = response.get("payload") or []
        return {p["id"]: p.get("name", str(p["id"])) for p in pools if p.get("count", 0) > 0}

    def pool_report(self, rep_from: str, rep_to_excl: str) -> List[dict]:
        payload = self.post_json(
            "/report/pools",
            {"from": rep_from, "to": rep_to_excl, "interval": "HOURS"},
        )
        return payload.get("payload") or []

    def load_users_usage_by_day(self, report_start_day: date, report_end_day: date) -> Dict[date, int]:
        """POST /report/users with interval DAYS; map periodStart calendar day -> totalUsersCount."""
        start_day = f"{report_start_day.isoformat()} 00:00:00.000"
        end_day = (
            f"{(report_end_day + timedelta(days=1)).isoformat()} 00:00:00.000"
        )
        api_response = self.post_json(
            "/report/users",
            {
                "from": start_day,
                "to": end_day,
                "interval": "DAYS",
            },
        )
        payload = api_response.get("payload")
        if not isinstance(payload, list):
            raise SystemExit(f"Unexpected /report/users response: {api_response!r}")
        counts_by_calendar_day: Dict[date, int] = {}
        for day_data in payload:
            if not isinstance(day_data, dict):
                continue
            period_day_str = PoolUtilization.period_start_day(day_data.get("periodStart"))
            if not period_day_str:
                continue
            try:
                calendar_day = datetime.strptime(period_day_str, ReportSchema.DATE_FMT_SHORT).date()
            except ValueError:
                continue
            total_users_count = day_data.get("totalUsersCount")
            if total_users_count is not None:
                counts_by_calendar_day[calendar_day] = int(total_users_count)
                continue
            fallback_username_list = day_data.get("totalUsers")
            counts_by_calendar_day[calendar_day] = (
                len(fallback_username_list) if isinstance(fallback_username_list, list) else 0
            )
        return counts_by_calendar_day

    def unique_active_usernames_in_range(self, range_start_day: date, range_end_day: date) -> Set[str]:
        """Distinct usernames active on at least one day in [range_start_day, range_end_day] (inclusive).

        Calls ``POST /report/users`` with ``interval`` ``DAYS`` and unions each bucket's ``totalUsers``.
        If a day has counts but no ``totalUsers`` list, that day cannot contribute names; see stderr warning.
        """
        range_start_iso = f"{range_start_day.isoformat()} 00:00:00.000"
        range_end_exclusive_iso = (
            f"{(range_end_day + timedelta(days=1)).isoformat()} 00:00:00.000"
        )
        api_response = self.post_json(
            "/report/users",
            {
                "from": range_start_iso,
                "to": range_end_exclusive_iso,
                "interval": "DAYS",
            },
        )
        daily_buckets = api_response.get("payload")
        if not isinstance(daily_buckets, list):
            raise SystemExit(f"Unexpected /report/users response: {api_response!r}")
        unique_names: Set[str] = set()
        buckets_without_usernames = 0
        for day_bucket in daily_buckets:
            if not isinstance(day_bucket, dict):
                continue
            username_list = day_bucket.get("totalUsers")
            if isinstance(username_list, list) and username_list:
                for name in username_list:
                    if name is not None and str(name).strip():
                        unique_names.add(str(name).strip())
            elif day_bucket.get("totalUsersCount"):
                buckets_without_usernames += 1
        if buckets_without_usernames:
            print(
                f"[WARN] {buckets_without_usernames} daily bucket(s) had totalUsersCount but no "
                "totalUsers list; unique count may be understated.",
                file=sys.stderr,
            )
        return unique_names


class PoolUtilization:
    @staticmethod
    def period_start_day(period_start) -> Optional[str]:
        if period_start is None:
            return None
        s = str(period_start)
        return s[:10] if len(s) >= 10 else None

    @classmethod
    def analyse_pool(cls, records: List[dict]) -> Dict[str, Dict[str, float]]:
        stats: Dict[str, Dict[str, float]] = {}
        for label, key in ReportSchema.RESOURCE_KEYS.items():
            divisor = GIB if key == "memory" else 1
            actives: List[float] = []
            total_cap = 0.0
            for record in records:
                request_stat = (record.get("requestsStats") or {}).get(key) or {}
                actives.append((request_stat.get("active") or 0) / divisor)
                total = (request_stat.get("total") or 0) / divisor
                if total > total_cap:
                    total_cap = total
            if not actives:
                actives = [0.0]
            stats[label] = {
                "max": max(actives),
                "avg": sum(actives) / len(actives),
                "total": total_cap,
            }
        return stats

    @classmethod
    def group_records_by_day(cls, pools_payload: List[dict], active_pool_ids: Set[int]) -> Dict[str, List[dict]]:
        by_day: DefaultDict[str, List[dict]] = defaultdict(list)
        for pool in pools_payload or []:
            pool_id = pool.get("poolId")
            if pool_id not in active_pool_ids:
                continue
            for record in pool.get("records") or []:
                period_day_key = cls.period_start_day(record.get("periodStart"))
                if period_day_key:
                    by_day[period_day_key].append(record)
        return dict(by_day)


class TsvOutput:
    @staticmethod
    def last_synced_date(path: str) -> Optional[date]:
        if not os.path.isfile(path):
            return None
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        if len(lines) < 2:
            return None
        latest_date: Optional[date] = None
        for line in lines[1:]:
            line = line.strip()
            if not line:
                continue
            cell = line.split("\t", 1)[0].strip()[:10]
            try:
                row_date = datetime.strptime(cell, ReportSchema.DATE_FMT_SHORT).date()
            except ValueError:
                continue
            latest_date = row_date if latest_date is None or row_date > latest_date else latest_date
        return latest_date

    @classmethod
    def verify_header(cls, path: str) -> None:
        if not os.path.isfile(path):
            return
        with open(path, encoding="utf-8") as f:
            first = f.readline().rstrip("\n")
        expected = ReportSchema.expected_header()
        if first and first != expected:
            raise SystemExit(
                f"Output file header does not match expected schema.\n"
                f"Got:      {first[:120]}...\n"
                f"Expected: {expected[:120]}...\n"
                f"Rename or remove {path!r} before running."
            )


class DayFailureCounts:
    """Per day FAILURE and STOPPED counts from the filtered /run/filter run list by end day."""

    def __init__(self, by_day: Dict[date, Tuple[int, int]]) -> None:
        self.by_day = by_day

    @classmethod
    def from_filtered_runs(
        cls, runs: List[dict], report_start_day: date, report_end_day: date
    ) -> "DayFailureCounts":
        """Count FAILURE/STOPPED runs by endDate calendar day in the report range"""
        failures: DefaultDict[date, int] = defaultdict(int)
        stopped: DefaultDict[date, int] = defaultdict(int)
        for run in runs:
            status = RunFields.run_status_str(run)
            if status == "FAILURE":
                target = failures
            elif status == "STOPPED":
                target = stopped
            else:
                continue
            end = RunTimeline.parse_run_datetime(run.get("endDate"))
            if end is None:
                continue
            end = RunTimeline.normalize_naive_utc(end)
            end_calendar_day = end.date()
            if report_start_day <= end_calendar_day <= report_end_day:
                target[end_calendar_day] += 1
        by_day: Dict[date, Tuple[int, int]] = {}
        report_day = report_start_day
        while report_day <= report_end_day:
            by_day[report_day] = (failures[report_day], stopped[report_day])
            report_day += timedelta(days=1)
        return cls(by_day)


class _DayAccumulators:
    def __init__(
        self,
        starts_by_day: DefaultDict[date, int],
        compute_cpu: DefaultDict[date, float],
        compute_gpu: DefaultDict[date, float],
        cb_hours: DefaultDict[date, float],
        outside_hours: DefaultDict[date, float],
        cb_run_ids: DefaultDict[date, Set[object]],
        outside_run_ids: DefaultDict[date, Set[object]],
        top_pipeline: DefaultDict[date, DefaultDict[str, Dict[str, object]]],
        top_docker: DefaultDict[date, DefaultDict[str, Dict[str, object]]],
        interactive: DefaultDict[date, int],
        batch: DefaultDict[date, int],
        workload_class_counts: DefaultDict[date, DefaultDict[str, int]],
        duration_hours: DefaultDict[date, DefaultDict[str, float]],
    ) -> None:
        self.starts_by_day = starts_by_day
        self.compute_cpu = compute_cpu
        self.compute_gpu = compute_gpu
        self.cb_hours = cb_hours
        self.outside_hours = outside_hours
        self.cb_run_ids = cb_run_ids
        self.outside_run_ids = outside_run_ids
        self.top_pipeline = top_pipeline
        self.top_docker = top_docker
        self.interactive = interactive
        self.batch = batch
        self.workload_class_counts = workload_class_counts
        self.duration_hours = duration_hours


class DailyMetricsBuilder:
    def __init__(self) -> None:
        self._decimals = max(0, FLOAT_DECIMAL)
        self._decimal_format = f"{{:.{self._decimals}f}}"

    @staticmethod
    def _count_starts_interactive_batch_by_day(
        runs: List[dict], report_start_day: date, report_end_day: date
    ) -> Tuple[DefaultDict[date, int], DefaultDict[date, int], DefaultDict[date, int]]:
        """Total counts and interactive/batch split by start calendar day."""
        starts_by_day: DefaultDict[date, int] = defaultdict(int)
        interactive: DefaultDict[date, int] = defaultdict(int)
        batch: DefaultDict[date, int] = defaultdict(int)
        for run in runs:
            start = RunTimeline.parse_run_datetime(run.get("startDate"))
            if start is None:
                continue
            start = RunTimeline.normalize_naive_utc(start)
            start_calendar_day = start.date()
            if report_start_day <= start_calendar_day <= report_end_day:
                starts_by_day[start_calendar_day] += 1
                cmd = (run.get("cmdTemplate") or "").strip()
                if cmd == INTERACTIVE_CMD:
                    interactive[start_calendar_day] += 1
                else:
                    batch[start_calendar_day] += 1
        return starts_by_day, interactive, batch

    @staticmethod
    def _tsv_escape(text: object) -> str:
        if text is None:
            return ""
        return str(text).replace("\t", " ").replace("\r", " ").replace("\n", " ")

    def _top_three_runs(
        self,
        case_day: Dict[str, Dict[str, object]],
        decimal_format: str,
    ) -> List[str]:
        ranked = sorted(
            case_day.items(),
            key=lambda x: (-float(x[1]["hours"]), self._tsv_escape(x[0])),
        )[:3]
        cells: List[str] = []
        for j in range(3):
            if j < len(ranked):
                name, data = ranked[j]
                cells.extend(
                    [
                        self._tsv_escape(name),
                        decimal_format.format(float(data["hours"])),
                        decimal_format.format(float(data["cost"])),
                        str(len(data["run_ids"]))
                    ]
                )
            else:
                cells.extend(["", decimal_format.format(0.0), decimal_format.format(0.0), "0"])
        return cells

    def build_rows(
        self,
        report_start_day: date,
        report_end_day: date,
        runs: List[dict],
        now_naive: datetime,
        cb_types: Set[str],
        pool_by_day: Dict[str, Dict[str, Dict[str, float]]],
        failures: DayFailureCounts,
        users_active_by_day: Dict[date, int],
        new_users_onboarded_by_day: Dict[date, int],
    ) -> List[str]:
        starts_by_day, interactive_by_day, batch_by_day = (
            self._count_starts_interactive_batch_by_day(runs, report_start_day, report_end_day)
        )
        data_by_date = _DayAccumulators(
            starts_by_day=starts_by_day,
            compute_cpu=defaultdict(float),
            compute_gpu=defaultdict(float),
            cb_hours=defaultdict(float),
            outside_hours=defaultdict(float),
            cb_run_ids=defaultdict(set),
            outside_run_ids=defaultdict(set),
            top_pipeline=defaultdict(
                lambda: defaultdict(lambda: {"hours": 0.0, "cost": 0.0, "run_ids": set()})
            ),
            top_docker=defaultdict(
                lambda: defaultdict(lambda: {"hours": 0.0, "cost": 0.0, "run_ids": set()})
            ),
            interactive=interactive_by_day,
            batch=batch_by_day,
            workload_class_counts=defaultdict(lambda: defaultdict(int)),
            duration_hours=defaultdict(lambda: defaultdict(float)),
        )

        for run in runs:
            start = RunTimeline.parse_run_datetime(run.get("startDate"))
            if start is None:
                continue
            start = RunTimeline.normalize_naive_utc(start)
            end_eff = RunTimeline.effective_run_end(run, now_naive)
            if end_eff <= start:
                continue

            status = RunFields.run_status_str(run)
            span = RunTimeline.overlapping_day_range(start, end_eff, report_start_day, report_end_day)
            if span is None:
                continue
            overlap_first_day, overlap_last_day = span

            node_type = ((run.get("instance") or {}).get("nodeType") or "").strip()
            in_cb = node_type in cb_types if cb_types else False
            workload_key = RunFields.run_workload_key(run)
            price = float(run.get("pricePerHour") or 0)
            run_id = run.get("id")
            run_key = run_id if run_id is not None else id(run)
            pipeline_name = RunFields.pipeline_name_field(run)
            docker_name = RunFields.short_image(run.get("dockerImage") or "")

            calendar_day = overlap_first_day
            while calendar_day <= overlap_last_day:
                day_window_start, day_window_end_exclusive = RunTimeline.day_window(calendar_day)
                wall_sec = RunTimeline.overlap_seconds(
                    start, end_eff, day_window_start, day_window_end_exclusive
                )
                if wall_sec <= 0:
                    calendar_day += timedelta(days=1)
                    continue

                paused_seconds = RunTimeline.paused_seconds_in_day_window(
                    run,
                    start,
                    end_eff,
                    day_window_start,
                    day_window_end_exclusive,
                )
                active_sec = max(0.0, wall_sec - paused_seconds)
                if active_sec > 0:
                    active_hours = active_sec / 3600.0
                    if in_cb:
                        data_by_date.cb_hours[calendar_day] += active_hours
                        data_by_date.cb_run_ids[calendar_day].add(run_key)
                    else:
                        data_by_date.outside_hours[calendar_day] += active_hours
                        data_by_date.outside_run_ids[calendar_day].add(run_key)

                    if RunFields.is_gpu_instance(node_type):
                        data_by_date.compute_gpu[calendar_day] += active_hours
                    else:
                        data_by_date.compute_cpu[calendar_day] += active_hours

                    if status in ("SUCCESS", "FAILURE", "STOPPED", "RUNNING"):
                        docker_top_aggregate = data_by_date.top_docker[calendar_day][docker_name]
                        docker_top_aggregate["hours"] = float(docker_top_aggregate["hours"]) + active_hours
                        docker_top_aggregate["cost"] = float(docker_top_aggregate["cost"]) + active_hours * price
                        docker_top_aggregate["run_ids"].add(run_key)
                        if pipeline_name is not None:
                            pipeline_top_aggregate = data_by_date.top_pipeline[calendar_day][pipeline_name]
                            pipeline_top_aggregate["hours"] = (
                                float(pipeline_top_aggregate["hours"]) + active_hours
                            )
                            pipeline_top_aggregate["cost"] = (
                                float(pipeline_top_aggregate["cost"]) + active_hours * price
                            )
                            pipeline_top_aggregate["run_ids"].add(run_key)

                    if workload_key in WORKLOAD_KEYS:
                        data_by_date.duration_hours[calendar_day][workload_key] += active_hours
                        data_by_date.workload_class_counts[calendar_day][workload_key] += 1

                calendar_day += timedelta(days=1)

        lines: List[str] = []
        report_day = report_start_day
        decimal_format = self._decimal_format
        while report_day <= report_end_day:
            lines.append(
                self._format_day_row(
                    report_day,
                    data_by_date,
                    pool_by_day,
                    failures,
                    decimal_format,
                    users_active_by_day,
                    new_users_onboarded_by_day,
                )
            )
            report_day += timedelta(days=1)
        return lines

    def _format_day_row(
        self,
        report_day: date,
        day_data: _DayAccumulators,
        pool_by_day: Dict[str, Dict[str, Dict[str, float]]],
        failures: DayFailureCounts,
        decimal_format: str,
        users_active_by_day: Dict[date, int],
        new_users_onboarded_by_day: Dict[date, int],
    ) -> str:
        day_str = report_day.isoformat()
        pool_stats = pool_by_day.get(day_str) or {
            "CPU": {"total": 0.0, "avg": 0.0, "max": 0.0},
            "GPU": {"total": 0.0, "avg": 0.0, "max": 0.0},
            "RAM (GiB)": {"total": 0.0, "avg": 0.0, "max": 0.0},
        }
        capacity_block_hours = day_data.cb_hours[report_day]
        outside_hours = day_data.outside_hours[report_day]
        total_hours = capacity_block_hours + outside_hours

        top_pipeline_cells = self._top_three_runs(day_data.top_pipeline[report_day], decimal_format)
        top_docker_cells = self._top_three_runs(day_data.top_docker[report_day], decimal_format)

        failure_count, stopped_count = failures.by_day.get(report_day, (0, 0))
        total_jobs = day_data.starts_by_day[report_day]

        row = [
            day_str,
            str(users_active_by_day.get(report_day, 0)),
            str(new_users_onboarded_by_day.get(report_day, 0)),
            str(total_jobs),
            decimal_format.format(day_data.compute_cpu[report_day]),
            decimal_format.format(day_data.compute_gpu[report_day]),
            decimal_format.format(pool_stats["CPU"]["total"]),
            decimal_format.format(pool_stats["GPU"]["total"]),
            decimal_format.format(pool_stats["RAM (GiB)"]["total"]),
            decimal_format.format(pool_stats["CPU"]["avg"]),
            decimal_format.format(pool_stats["GPU"]["avg"]),
            decimal_format.format(pool_stats["RAM (GiB)"]["avg"]),
            decimal_format.format(pool_stats["CPU"]["max"]),
            decimal_format.format(pool_stats["GPU"]["max"]),
            decimal_format.format(pool_stats["RAM (GiB)"]["max"]),
            decimal_format.format(capacity_block_hours),
            decimal_format.format(outside_hours),
            decimal_format.format(total_hours),
            str(len(day_data.cb_run_ids[report_day])),
            str(len(day_data.outside_run_ids[report_day])),
            *top_pipeline_cells,
            *top_docker_cells,
            str(day_data.interactive[report_day]),
            str(day_data.batch[report_day]),
            str(day_data.workload_class_counts[report_day].get("gpu-heavy", 0)),
            str(day_data.workload_class_counts[report_day].get("cpu-heavy", 0)),
            str(day_data.workload_class_counts[report_day].get("memory-heavy", 0)),
            str(day_data.workload_class_counts[report_day].get("general-purpose", 0)),
            decimal_format.format(day_data.duration_hours[report_day].get("gpu-heavy", 0.0)),
            decimal_format.format(day_data.duration_hours[report_day].get("cpu-heavy", 0.0)),
            decimal_format.format(day_data.duration_hours[report_day].get("memory-heavy", 0.0)),
            decimal_format.format(day_data.duration_hours[report_day].get("general-purpose", 0.0)),
            str(failure_count),
            str(stopped_count),
        ]
        return "\t".join(row)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Daily usage metrics TSV; appends days after the last date in the output file."
    )
    p.add_argument(
        "--from",
        dest="date_from",
        default=DEFAULT_DATE_FROM,
        metavar="YYYY-MM-DD",
        help=f"First day when creating a new file (default: {DEFAULT_DATE_FROM}).",
    )
    p.add_argument(
        "--to",
        dest="date_to",
        default=None,
        metavar="YYYY-MM-DD",
        help="Last calendar day inclusive. Default: yesterday (UTC); today is skipped.",
    )
    p.add_argument(
        "--output",
        "-o",
        default="usage_metrics_by_day.tsv",
        help="Output TSV path (default: %(default)s).",
    )
    p.add_argument(
        "--lookback-days",
        type=int,
        default=365,
        help="Subtract N days from the first day to fetch for /run/filter (default: 365).",
    )
    p.add_argument(
        "--page-size",
        type=int,
        default=100,
        help="Page size for /run/filter (default: %(default)s).",
    )
    p.add_argument(
        "--rewrite",
        action="store_true",
        help="Ignore existing file and write from --from through --to (overwrites).",
    )
    return p.parse_args()


def main() -> None:
    args = parse_args()
    api_base = os.environ.get("API", "").rstrip("/")
    token = os.environ.get("API_TOKEN", "")
    if not api_base or not token:
        sys.exit("API and API_TOKEN environment variables are required.")

    out_path = args.output
    if not args.rewrite:
        TsvOutput.verify_header(out_path)

    yesterday = default_report_day_utc()
    date_to_str = args.date_to or yesterday.isoformat()
    report_end_day = datetime.strptime(date_to_str, ReportSchema.DATE_FMT_SHORT).date()

    if args.rewrite:
        report_start_day = datetime.strptime(args.date_from, ReportSchema.DATE_FMT_SHORT).date()
    else:
        last = TsvOutput.last_synced_date(out_path)
        if last is None:
            report_start_day = datetime.strptime(args.date_from, ReportSchema.DATE_FMT_SHORT).date()
        else:
            report_start_day = last + timedelta(days=1)

    if report_start_day > report_end_day:
        prev_last = TsvOutput.last_synced_date(out_path)
        print(f"No new days to write (last in file: {prev_last}, next would be {report_start_day}, "
              f"--to={report_end_day}).", file=sys.stderr)
        return

    client = PipelineRestClient(api_base, token)

    lookback = max(0, args.lookback_days)
    api_start_day = report_start_day - timedelta(days=lookback)
    start_date_from = f"{api_start_day.isoformat()} 00:00:00.000"

    print(
        f"Fetching runs from startDateFrom={start_date_from!r} "
        f"for report days {report_start_day} .. {report_end_day} …",
        file=sys.stderr,
    )
    runs = client.fetch_all_runs(
        start_date_from,
        args.page_size,
        ReportSchema.RUN_FETCH_STATUSES,
    )
    print(f"Loaded {len(runs)} run(s).", file=sys.stderr)

    users = client.load_users()
    excluded_owners = owners_matching_excluded_user_groups(users, EXCLUDED_USER_GROUPS_LOWER)
    run_number_before = len(runs)
    runs = [r for r in runs if not RunFields.run_matches_excluded_owner(r, excluded_owners)]
    print(
        f"Excluded {run_number_before - len(runs)} run(s) whose owner is among "
        f"{len(excluded_owners)} user(s) with roles/groups in {sorted(EXCLUDED_USER_GROUPS_LOWER)!r}.",
        file=sys.stderr,
    )

    cb_types = client.capacity_block_instance_types()
    if not cb_types:
        print("[WARN] No capacity block instance types in preferences; capacity block metrics will be 0.",
              file=sys.stderr)

    now_naive = datetime.now(timezone.utc).replace(tzinfo=None)

    pool_by_day: Dict[str, Dict[str, Dict[str, float]]] = {}
    active = client.active_pool_ids()
    if active:
        rep_from = f"{report_start_day.isoformat()} 00:00:00.000"
        rep_to_excl = (report_end_day + timedelta(days=1)).isoformat() + " 00:00:00.000"
        pool_payload = client.pool_report(rep_from, rep_to_excl)
        by_day_recs = PoolUtilization.group_records_by_day(pool_payload, set(active.keys()))
        for day_str, recs in by_day_recs.items():
            pool_by_day[day_str] = PoolUtilization.analyse_pool(recs)
    else:
        print("Warning: no active pools; capacity columns will be 0.", file=sys.stderr)

    failures = DayFailureCounts.from_filtered_runs(runs, report_start_day, report_end_day)

    users_active_by_day = client.load_users_usage_by_day(report_start_day, report_end_day)

    new_users_onboarded_by_day = new_users_onboarded_by_calendar_day(users, report_start_day, report_end_day)

    builder = DailyMetricsBuilder()
    rows = builder.build_rows(
        report_start_day,
        report_end_day,
        runs,
        now_naive,
        cb_types,
        pool_by_day,
        failures,
        users_active_by_day,
        new_users_onboarded_by_day,
    )

    mode = "w" if args.rewrite or not os.path.isfile(out_path) else "a"
    with open(out_path, mode, encoding="utf-8") as f:
        if mode == "w":
            f.write(ReportSchema.expected_header() + "\n")
        f.write("\n".join(rows) + "\n")

    print(
        f"Wrote {len(rows)} row(s) to {out_path} (mode={'truncate' if mode == 'w' else 'append'}).",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
