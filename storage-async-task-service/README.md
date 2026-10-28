# Cloud Pipeline Storage Async Task Service

A Python service that executes the platform's asynchronous storage tasks. Today there is one kind:
`DOWNLOAD_ARCHIVE` - "download this folder as one archive".

The platform API does not build archives. It records the request as a task, and this service claims
it, reads the selected files out of the data storage, packs them into a ZIP, uploads the result to a
dedicated bucket and reports the task as finished. The user then asks the API for a signed link.

It never accepts an inbound request: it polls the API, which is the only state it has.

## What one task goes through

```
POST /datastorage/{id}/download/archive     the API checks the rights and records a PENDING task
POST /async-task/next                       this service claims it, the API makes it RUNNING
                                            read the selection, check the thresholds
                                            download file by file, write each into the ZIP
                                            upload the ZIP to the archive bucket
POST /async-task/{id}/status                SUCCESS, with the size and the skipped count
GET  /async-task/{id}/consume               the user gets a signed link
```

A cancelled task stops within `--status-poll-seconds` and deletes what it had built. A task whose
worker was killed is finished by the cleaner, which also expires archives past `ttlDays`.

Permissions are never evaluated here. The API does that once, when the task is created.

## Requirements

- **Python 3.12.** The code uses `StrEnum`, `match` and `datetime.UTC`.
- `boto3`, pinned in `requirements.txt`.
- **pipe-common**, the platform's API client from `workflows/pipe-common`. It is on no package index.
- Credentials that may *assume* the roles the platform records - on a platform node, its instance
  profile. The service holds no keys of its own.
- For NFS storages only: the `mount` command, and root or a `sudo` rule for it.

## Running it locally

```bash
cd storage-async-task-service
python3 -m venv .venv
source .venv/bin/activate              # Windows: .venv\Scripts\activate
pip install -e . -r requirements-dev.txt
pip install -e ../workflows/pipe-common
```

Then point it at a deployment and run one round:

```bash
read -rs CP_API_JWT_ADMIN && export CP_API_JWT_ADMIN   # an admin JWT; not echoed
export API=https://your-deployment.example.com/pipeline/restapi

python -m sats.app --once --slots 1 --work-dir ./tasks --log-level DEBUG
```

`--once` does one round - preference, cleanups, claim - waits for whatever it started, and exits.
Without it the service loops until `Ctrl-C`, which asks it to stop claiming and wait for its workers.

## Settings

Every setting is both an option and an environment variable, and the option wins.
`python -m sats.app --help` prints them all.

| Option | Environment | Default | What it is |
|---|---|---|---|
| `--cp-api-url` | `API`, or `CP_API_SRV_INTERNAL_HOST` + `CP_API_SRV_INTERNAL_PORT` | - | Base url of the API, including `/restapi`. Required. Without `API`, both of the other two are needed. |
| `--cp-api-token` | `CP_API_JWT_ADMIN` | - | Administrator JWT. Required. Never logged. |
| `--verify-ssl` | `CP_SATS_VERIFY_SSL` | off | Has no effect: the platform's API client never verifies. Asking for it logs a warning at startup. |
| `--work-dir` | `CP_SATS_WORK_DIR` | `/tmp/sats` | Scratch space. Needs room for the largest allowed archive times `--slots`. |
| `--slots` | `CP_SATS_SLOTS` | `2` | Tasks at once. One slot is one thread. |
| `--poll-interval-seconds` | `CP_SATS_POLL_INTERVAL` | `20` | Sleep between rounds. |
| `--cleanup-interval-seconds` | `CP_SATS_CLEANUP_INTERVAL` | `300` | How often the cleanups run. `0` turns them off, and the service warns. |
| `--status-poll-seconds` | `CP_SATS_STATUS_POLL` | `15` | How often a worker checks whether its task was cancelled. |
| `--part-size-mb` | `CP_SATS_PART_SIZE_MB` | `8` | Upload part size, raised automatically for an archive above 80 GiB. |
| `--compress-level` | `CP_SATS_COMPRESS_LEVEL` | `1` | ZIP deflate level. Low, because research data is usually already compressed. |
| `--nfs-mount-root` | `CP_SATS_NFS_MOUNT_ROOT` | `/mnt/sats-shares` | Where NFS shares are mounted. |
| `--nfs-mount-timeout-seconds` | `CP_SATS_NFS_MOUNT_TIMEOUT` | `30` | Hard limit on `mount` and on the liveness check of a mount point. |
| `--once` | `CP_SATS_ONCE` | off | One round, then exit. |
| `--log-level` | `CP_SATS_LOG_LEVEL` | `INFO` | |
| `--log-dir` | `CP_SATS_LOG_DIR` | - | A directory adds a log file rotated at midnight, beside stdout. |
| `--log-backup-days` | `CP_SATS_LOG_BACKUP_DAYS` | `31` | How many rotated files to keep. |

## The platform preference

Everything an administrator tunes is in `storage.download.archive.config`, read again on every round,
so a change applies without a restart:

```json
{
  "destinationStorageId": 9,
  "destinationPrefix": "archives/",
  "maxSizeBytes": 107374182400,
  "maxFilesCount": 100000,
  "ttlDays": 7,
  "maxUserTasks": 3,
  "workerMaxRunningMinutes": 240,
  "stuckTasksThresholdMinutes": 360
}
```

Without `destinationStorageId` nothing is claimed and tasks stay pending. `null` in either threshold
means no limit. `stuckTasksThresholdMinutes` must exceed `workerMaxRunningMinutes`, or the cleaner
fails tasks whose workers are still working.

## How it is deployed

| Where | What it does |
|---|---|
| root `build.gradle` | builds the module and ships `dist/sats.tar.gz` in the distribution |
| `deploy/docker/cp-storage-async-task-service/` | the image, built and pushed by `build-dockers.sh` and by the `build-pipectl.yml` matrix |
| `deploy/contents/k8s/cp-storage-async-task-service/` | the pod: `envFrom: cp-config-global`, the liveness probe, logs and scratch space on the node |
| `deploy/contents/install/app/install.sh` | labels the node and deploys it as `cp-storage-async-task-service` |

The pod is privileged, because the service mounts NFS shares itself. The scratch directory needs room
for `--slots` archives of up to `maxSizeBytes` each, so point
`CP_STORAGE_ASYNC_TASK_SERVICE_KUBE_NODE_NAME` at a node with a disk for it.

An assembled artefact reports the platform's version, the build number and the commit included:
`sats --version` answers `0.25.0.42.<commit>`. The installed package carries the same version in the
form packaging accepts, with the part that is not a number after a `+`, so `pip show sats` in the
image answers `0.25.0.42+<commit>` instead. The two differ by that character and nothing else - see
`sats.util.version.pep440`, which exists because setuptools refuses the first form outright.

## Tests

```bash
.venv/bin/python -m pytest
.venv/bin/python -m flake8 sats tests
```

The tests touch nothing outside the process: no API, no S3, no network. The stand-ins are in
`tests/fakes.py`.
