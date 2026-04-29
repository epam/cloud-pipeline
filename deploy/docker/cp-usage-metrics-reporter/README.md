# Usage Metrics Reporter

Builds a **daily usage metrics** TSV for Cloud Pipeline, then optionally uploads it to **object storage** using the **`pipe` CLI**.

---

## Usage metrics report (`usage_metrics_daily_report.py`)

The script builds a **tab-separated** daily usage metrics file and **appends** new days after the last date already present.

### Output

If the output file exists, the script finds the latest date in the first column, then writes rows for **(last_date + 1)** through **`--to`** (inclusive). If the file is missing or has no data rows, it starts from **`--from`**.

Default **`--from`** is **`2026-01-01`** (overridable via **`CP_USAGE_METRICS_DEFAULT_DATE_FROM`**). Default **`--to`** is **yesterday (UTC)**. Omitting **`--to`** still ends at yesterday so the current day is not included.

### Active users trend

Daily **user** columns come from the **user-activity report**:

| Column | Source | Meaning                                                                                                                                                                                                                                              |
|--------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`total_active_users`** | **`POST /report/users`** with **`interval`** **`DAYS`** over the same calendar window as the row batch (`from` / `to` use **`YYYY-MM-DD 00:00:00.000`**). | Distinct users with platform activity that day — **`totalUsersCount`** from each daily data.|
| **`new_users_onboarded`** | **`GET /users`**. | Count of users whose **`registrationDate`** falls on that calendar day (within **`--from`** / **`--to`**). **Not** filtered by **`CP_USAGE_METRICS_EXCLUDE_USER_GROUPS`** (that applies only to runs).|

### Total jobs executed and interactive vs batch

The column **Total jobs executed** is the number of runs whose **startDate** falls on that **calendar day**. Each run is counted **once**, on its **start day only**, so summing the column over a date range equals the number of runs that **started** in that range among rows returned by **`/run/filter`** (no double-count across days). 

**`jobs_interactive`** / **`jobs_batch`**: same **start-day** rule as total jobs. Each run that starts on that calendar day is counted once (**`cmdTemplate`** = **`sleep infinity`** vs other).

### Compute CPU / GPU hours

**`compute_cpu_hours`** / **`compute_gpu_hours`**: active run time (hours) on that calendar day, split by instance **`nodeType`** family:
* **GPU** - the family prefix starts with any of **`CP_USAGE_METRICS_GPU_FAMILY_PREFIXES`** (default **`g`**, **`p`**)
* **CPU** - otherwise

### Top pipelines and Docker images

**Top 3 by pipeline name** and **top 3 by Docker image** are separate rankings (by active hours per day). 
* Both include **SUCCESS**, **FAILURE**, **STOPPED**, and **RUNNING**. 
* Pipeline ranking only includes runs with a non-empty **`pipelineName`**. 
* When **`CP_USAGE_METRICS_PIPELINE_INCLUDE_CONFIG_NAME`** is True (default), a non-empty **`configName`** is appended to the pipeline label as `pipeline-name | test` so different configs are separate rows.

### Capacity Block hours

**`cb_hours`** / **`outside_hours`** / **`cb_outside_total_hours`**: **active time** (wall overlap minus run in the **PAUSED** status) per day on **Capacity Block** instance types vs others.

### Failures and stopped

**`failures`** / **`stopped`**: counts from the **`/run/filter`** runs with status **FAILURE** or **STOPPED** whose **endDate** calendar day is that day.

### Excluded users (`CP_USAGE_METRICS_EXCLUDE_USER_GROUPS`)

Optional comma-separated role or group names to exclude specific user's runs from metrics. **Default**: **`ROLE_ADMIN`**.

### Example

```bash
python3 deploy/docker/cp-usage-metrics-reporter/metrics \
        -o usage_metrics_by_day.tsv \
        --from 2026-01-01
```

---

## Building the image

From the repository root:

```bash
docker build -t usage-metrics-reporter:<version> \
  -f deploy/docker/cp-usage-metrics-reporter/Dockerfile \
  --build-arg CP_API_DIST_URL='...' \
  deploy/docker/cp-usage-metrics-reporter
```

The container command is **`/init`** (see `init` in this directory).

---

## Kubernetes (CronJob)

* The manifest **`deploy/contents/k8s/cp-usage-metrics-reporter/cp-usage-metrics-reporter-cron.yaml`** defines a **CronJob** whose schedule is **`${CP_USAGE_METRICS_REPORTER_CRON_SCHEDULE}`**. 
* Set **`CP_USAGE_METRICS_REPORTER_CRON_SCHEDULE`** in **`install-config`** (e.g. `0 0 * * *`).
  * Default `0 1 * * *`: run at 01:00 UTC every day.
* **Configuration:** 
  * environment variables are loaded from the ConfigMap **`cp-config-global`** (`envFrom`)
  * explicit env for output path, S3 URI, and extra CLI args

```bash
kubectl apply -f deploy/contents/k8s/cp-usage-metrics-reporter/cp-usage-metrics-reporter-cron.yaml
```

## Parameters

### Required (API access)

At least one of each pair must be satisfied so **`API`** and **`API_TOKEN`** are set before the Python script runs.

| Parameter | Description                                                                                                                      |
|-----------|----------------------------------------------------------------------------------------------------------------------------------|
| **API** | Cloud Pipeline REST API base URL.                                                                                                |
| **CP_API_SRV_INTERNAL_HOST** + **CP_API_SRV_INTERNAL_PORT** | If **API** is unset, **init** sets `API` to `https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/restapi/`. |
| **API_TOKEN** | Bearer JWT for API calls used by the report script.                                                                              |
| **CP_API_JWT_ADMIN** | If **API_TOKEN** is unset, **init** exports **API_TOKEN** from this value.                                                       |

### Optional (**init** / upload)

| Parameter | Default | Description |
|-----------|---------|-------------|
| **CP_USAGE_METRICS_OUTPUT** | `/opt/cp-usage-metrics-reporter/data/usage_metrics_by_day.tsv` | Path of the generated TSV on disk.|
| **CP_USAGE_METRICS_EXTRA_ARGS** | *(empty)* | Extra arguments appended to the **`usage_metrics_daily_report.py`** invocation (e.g. `--from`, `--to`, `--rewrite`, `--lookback-days`, `--page-size`). |
| **CP_USAGE_METRICS_S3_URI** | *(unset)* | If set, **init** runs **`pipe storage cp`** to upload the TSV to this URI (e.g. `s3://DATA_STORAGE/metrics/usage_metrics_by_day.tsv`). If unset, the job exits **0** after writing the file (no upload). |

### Optional (report script)

| Parameter | Default                                            | Description                                                                                                                                                                  |
|-----------|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **CP_USAGE_METRICS_DEFAULT_DATE_FROM** | `2026-01-01`                                       | Default **`--from`** when the output file is new or empty.                                                                                                                   |
| **CP_USAGE_METRICS_WORKLOAD_KEYS** | `gpu-heavy,cpu-heavy,memory-heavy,general-purpose` | Comma-separated workload keys for duration / workload-class columns.                                                                                                         |
| **CP_USAGE_METRICS_GPU_FAMILY_PREFIXES** | `g,p`                                              | Comma-separated instance family prefixes treated as GPU for CPU vs GPU hour split.                                                                                           |
| **CP_USAGE_METRICS_EXCLUDE_USER_GROUPS** | `ROLE_ADMIN`                                       | Comma-separated role or group names; runs whose owner matches excluded users are omitted.                                                                                    |
| **CP_USAGE_METRICS_PIPELINE_INCLUDE_CONFIG_NAME** | `True`                                             | If **True**, top-pipeline keys include **`configName`** when set. If **False**, only **`pipelineName`** is used.|

---

## Overview

1. Runs **`python3`** on **`/opt/cp-usage-metrics-reporter/metrics/usage_metrics_daily_report.py`** with **`-o`** set to **CP_USAGE_METRICS_OUTPUT** and **CP_USAGE_METRICS_EXTRA_ARGS**, producing or appending the daily TSV.
2. If **CP_USAGE_METRICS_S3_URI** is unset, logs that upload is skipped and exits successfully.
3. If it is set, configures **`pipe`** with the same **API** / token, then **`pipe storage cp`** uploads the file to that URI; failures exit non-zero.
