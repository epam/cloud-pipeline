# Cloud Pipeline Storage Async Task Service

A Python service that executes the platform's asynchronous storage tasks. Two kinds today:

| Task type | What it does |
|---|---|
| `DOWNLOAD_ARCHIVE` | "download this folder as one archive" - packs a selection into a ZIP and uploads it to a dedicated bucket, which the user then gets a signed link to |
| `UNPACK_ARCHIVE` | "extract this archive" - takes an archive that lies in a data storage and writes its content into a storage and folder the caller named |

The platform API does neither. It records the request as a task, and this service claims it, does the
work and reports the task as finished.

It never accepts an inbound request: it polls the API, which is the only state it has.

## What one task goes through

```
POST /async-task/create                     the API checks the rights and records a PENDING task
POST /async-task/next?type=A&type=B         this service claims the oldest task of any type it runs
POST /async-task/{id}/status                SUCCESS, with what the task produced
```

Between the second and the third line the two types differ:

```
DOWNLOAD_ARCHIVE   read the selection, check the thresholds, download file by file into a ZIP,
                   upload the ZIP to the archive bucket
UNPACK_ARCHIVE     head the archive and read its tail for an entry count and a total, extract it
                   locally while counting bytes and checking every entry name, create the
                   destination folder and look once at it for collisions, then upload entry by entry
```

Inside the archive a file is named without the folders the selection has in common above the items
it names - `worker/archive/archive_preparation_worker._archive_root`. One folder picked deep in a tree is unpacked
under its own name (`sub1/sub2/folder` gives `folder/...`), and a selection spread across folders
keeps as much of the tree as it takes to tell its items apart (`sub1/sub2/folder` and
`sub1/sub3/folder` give `sub2/folder/...` and `sub3/folder/...`). One prefix for the whole selection,
not the parent of each item: taking the same prefix off every path cannot turn two paths that differ
in the storage into one name, while cutting each item back to its own name would - two folders
called `folder` would become one.

The paths are normalised before any of that, because an entry name that starts with `..` is written
outside the folder the archive is unpacked into by extractors that do not sanitise it. A path that
still climbs above the storage root once normalised - only a literal S3 key of that spelling does -
has its leading `..` dropped, which is the one place two different paths can still meet under one
name, and a path normalising to nothing at all is skipped and reported in the archive's note rather
than packed: `ZipFile.extractall` raises on an entry called `.` or `..` and abandons the rest of
the archive with it.

A cancelled task stops within `--status-poll-seconds`. A task whose worker was killed is finished by
the cleaner, which also expires archives past `ttlDays`.

**An extraction creates the folder it was asked to write into.** The caller named it, so a folder
that is not there yet is created with its parents rather than refused - the GUI always names a new
folder beside the archive. A file already at that path is refused instead, because a share cannot
hold both. On an object storage there is no folder to create at all, so an extraction that writes
nothing leaves no trace of the folder, while a share keeps it. On a share the folder is created with
this container's umask and owned by root, while the API sets the mode of a folder it creates from
`data.storage.nfs.default.umask` - group-writable by default. So a user may be unable to write into
a folder an extraction created, exactly as they may be unable to overwrite the files it wrote.

**A download task's archive is deleted when its task ends badly; an extraction's content is not.**
There is no rollback: a failed or cancelled extraction leaves the files it had already written where
they are, and says how many in the task's error message. A new run with `"overwrite": false` then
refuses exactly those files. Rolling back would need a journal of written keys, retries, a rule for
files that were replaced and the same logic again in the cleaner - and none of it brings back the
content an overwrite replaced.

Permissions are never evaluated here. The API does that once, when the task is created - READ on the
source, WRITE on the destination folder, and WRITE on the source as well for
`deleteSourceOnSuccess`. **The entry names inside an archive are checked by nothing but this
service** (`sats/worker/unpack/archive_extractor.py`): the API never opens the archive, this container runs as root
and its node holds a role of every storage on the platform, so a name leading out of the destination
is code execution. That is what every rule in `safe_entry_path` and every `is_inside` call is for.

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

**S3 storages work from a workstation; NFS ones do not.** Mounting a share needs `/proc/mounts` and
root, so off Linux a task on an NFS storage fails with the reason named rather than misbehaving. An
extraction between two S3 storages needs nothing but credentials that may assume the roles of both
storages' regions.

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
| `--nfs-mount-root` | `CP_SATS_NFS_MOUNT_ROOT` | `/mnt/sats-shares` | Where NFS shares are mounted: `<root>/ro/<share>` for reading and `<root>/rw/<share>` for an extraction's destination. The mode is a directory level so that one share needed both ways at once is two mounts. |
| `--nfs-mount-timeout-seconds` | `CP_SATS_NFS_MOUNT_TIMEOUT` | `30` | Hard limit on `mount` and on the liveness check of a mount point. |
| `--once` | `CP_SATS_ONCE` | off | One round, then exit. |
| `--log-level` | `CP_SATS_LOG_LEVEL` | `INFO` | |
| `--log-dir` | `CP_SATS_LOG_DIR` | - | A directory adds a log file rotated at midnight, beside stdout. |
| `--log-backup-days` | `CP_SATS_LOG_BACKUP_DAYS` | `31` | How many rotated files to keep. |

## The platform preference

Everything an administrator tunes is in `system.async.tasks.config`, read again on every round, so
a change applies without a restart:

```json
{
  "maxUserTasks": 3,
  "stuckTasksThresholdMinutes": 360,
  "tasks": {
    "storageDownloadAsArchive": {
      "destinationStorageId": 9,
      "destinationPrefix": "archives/",
      "maxSizeBytes": 107374182400,
      "maxFilesCount": 100000,
      "ttlDays": 7,
      "workerMaxRunningMinutes": 240
    },
    "storageUnpackArchive": {
      "maxArchiveSizeBytes": 53687091200,
      "maxSizeBytes": 214748364800,
      "maxFilesCount": 100000,
      "workerMaxRunningMinutes": 240
    }
  }
}
```

**A section per task type under `tasks`, and the shared values at the top.** `maxUserTasks` is one budget across
every type - the quota exists to stop one person occupying the service, and it is the same service
whichever task they queued - and `stuckTasksThresholdMinutes` governs the cleaner rather than a task
type, so it must exceed the **largest** `workerMaxRunningMinutes` of any section. Two of either would
be a trap, which is why neither sits inside `tasks`. A value put in the wrong level is read as
absent and silently replaced by its default, so the warnings this service logs at startup name the
level each setting is read from.

**The same names mean different things in the two sections**, which is why each is read and
validated on its own:

| Setting | In `storageDownloadAsArchive` | In `storageUnpackArchive` |
|---|---|---|
| `maxSizeBytes` | the size of the selection a user asked to be packed | the **uncompressed** total an archive may be extracted into |
| `maxFilesCount` | the file count of that selection | the **entry count** of one archive |
| `maxArchiveSizeBytes` | - | the archive object itself, checked before it is fetched |
| `ttlDays`, `destination*` | where a finished archive goes and how long it lives | - an extraction's content belongs to the storage the caller named, so the platform owns no artifact to expire |

**Each type is claimed only when its own section is usable.** Without
`tasks.storageDownloadAsArchive.destinationStorageId` no download task is claimed; without a
`tasks.storageUnpackArchive` section at all, no extraction is - its limits were never reviewed by an
administrator, and extracting under invented ones is worse than waiting. Either way the tasks stay
`PENDING`, so filling the preference in makes the backlog run. `null` in a limit means no limit.

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
