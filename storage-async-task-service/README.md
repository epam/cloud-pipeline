# Cloud Pipeline Storage Archive Service

A Python service that builds the ZIP archives users ask for when they select several files or folders in
a data storage and press download.

The platform API does not build them. It records the request as an asynchronous task
(`pipeline.async_task`, type `DOWNLOAD_ARCHIVE`) and answers immediately. This service claims those
tasks one at a time, reads the selected paths from the storage, packs them into a ZIP, uploads the
result to a dedicated bucket, and reports the task as finished. The user then asks the API for a signed
download url.

Nothing calls this service. It has no inbound port, no health endpoint and no queue of its own: it
polls the API, and the API is the only state it has.

## Contents

- [How a task flows through the service](#how-a-task-flows-through-the-service)
- [Requirements](#requirements)
- [Running it locally](#running-it-locally)
- [End to end: one archive by hand](#end-to-end-one-archive-by-hand)
- [Settings](#settings)
- [The platform preference](#the-platform-preference)
- [Running the tests and the linter](#running-the-tests-and-the-linter)
- [Troubleshooting](#troubleshooting)
- [The files, and what each one is for](#the-files-and-what-each-one-is-for)
- [How to read this Python](#how-to-read-this-python)

`ARCHITECTURE.md`, next to this file, is the other half: a map of what runs when, on which thread, in
which function, and which setting is consulted at that moment. Read it when you need to find something;
read this one when you need to know why it is the way it is.

## How a task flows through the service

```
  user selects paths in the GUI
            |
            v
  POST /datastorage/{id}/download/archive        the API checks the user's rights on the storage
            |                                    once, here, and records a PENDING task
            v
  +-------------------------------------+
  |  this service, one round of a loop  |
  +-------------------------------------+
            |
            |  1. GET /preferences/storage.download.archive.config   destination and thresholds
            |  2. four cleaner cleanups                                clean up after dead workers
            |  3. POST /async-task/next?type=DOWNLOAD_ARCHIVE        claim, while a slot is free
            |                                                        (the API moves PENDING -> RUNNING)
            v
  +-------------------------------------+
  |  one worker thread, one task        |
  +-------------------------------------+
            |
            |  GET /cloud/region/{id}  per storage: the role to assume and the region
            |                           to sign for, for the one being read and the one
            |                           the archive is written to
            |  list the selected paths, count files and bytes, check the thresholds
            |  download each file, write it into archive.zip as it goes
            |  upload the ZIP to <destinationPrefix><taskId>.zip in the destination storage
            |  POST /async-task/{id}/status  -> SUCCESS, with where the archive went and its size
            v
  user asks GET /async-task/{id}/consume and downloads the archive

  seven days later, the cleaner deletes the object and marks the task EXPIRED
```

Two things about this shape are worth knowing before reading the code.

**This service never evaluates permissions.** The API does it once, at task creation. The service runs
with a system administrator's token precisely because by the time it sees a task, the decision has
already been made, and re-checking as an administrator would be meaningless anyway.

**It assumes a role of the platform rather than using the host's own S3 access.** For each storage it
reads that storage's cloud region and assumes the role recorded there: the region's `iamRole`, else its
`tempCredentialsRole`, or - for a storage flagged `useAssumedCredentials` - the `tempCredentialsRole` of
the storage itself. The first two steps are `S3StorageProvider.getS3Helper` and the lifecycle service's
`_fetch_role_arn_for_storage`; the third is this service's own, because those two fall back to the host's
own S3 access where a region records no `iamRole`, and here the region's storage role is assumed instead.
The host therefore needs no access to any bucket - only credentials to assume with, which on a platform
node is its instance profile. Each client carries its own region, which is what lets a task read a
storage in one region and write the archive to another.
Assuming the role directly rather than asking `POST /datastorage/tempCredentials/` for ready credentials
is deliberate: botocore re-assumes by itself when the session is close to expiring, where credentials
fetched once would have to be watched and re-fetched by us, and an archive can take longer than they
live. `sats/cloud/aws.py` is that whole subject, and its module docstring is the long form of this paragraph.

**A task is never lost and never silently abandoned.** A worker ends its own task on every path it can
reach, including cancellation and its own timeout. For the one case it cannot handle - the process being
killed - the cleaner finishes the task from the outside. See the module docstring of `sats/worker/cleaner.py`.

## Requirements

- **Python 3.12.** Not "3.8 or newer": the code uses `StrEnum`, `match`, `datetime.UTC` and
  `Path.is_relative_to`, and will not import on an older interpreter.
- `boto3` - pinned in `requirements.txt`.
- **pipe-common**, the platform's own API client, from `workflows/pipe-common`. It is what talks to the
  API - see `sats/api.py` - and it is not on any package index, so locally it is
  `pip install -e ../workflows/pipe-common`. That pulls some forty packages, luigi and paramiko among
  them, because `pipeline/__init__.py` imports the whole of pipe-common; the service imports it inside
  one function so that the tests do not need any of it. **This service has no container yet** - there
  is no `deploy/docker/cp-storage-async-task-service/` - so how pipe-common reaches it in a deployment is
  still to be decided; `deploy/docker/cp-storage-lifecycle-service/Dockerfile` installs it from the API
  distribution, and that is the obvious shape to copy.
- **No S3 access of its own.** What the host does need is credentials that may *assume* the roles the
  platform records - on a platform node, its instance profile, the same thing the API server and the
  lifecycle service assume from. The role is the region's `iamRole` if it has one, otherwise its
  `tempCredentialsRole`; a storage flagged `useAssumedCredentials` names its own. A region with none of
  them leaves the client on the region's named profile and then on whatever `boto3` finds, which is the
  right answer for a single-account installation and nothing else.
- For NFS storages only: the `mount` command, and either root or a `sudo` rule for it. An NFS task on a
  laptop normally will not work, and that is fine - test with an object storage.

## Running it locally

The repository does not record how your machine is set up, so these are the plain commands. If
`.agents/localenv/` exists, use the Python version manager it names instead of the system `python3`.

**1. Check the interpreter.** Anything other than 3.12 here means the rest will not work:

```bash
cd storage-async-task-service
python3 --version
```

**2. Create a virtual environment and activate it.** A virtual environment is a private copy of the
interpreter's package directory. Without one, `pip install` writes into the system Python and the
service's pinned versions fight with everything else on the machine:

```bash
python3 -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
```

The shell prompt now starts with `(.venv)`. Everything below assumes that. `deactivate` leaves it, and
deleting the `.venv` directory undoes the whole step - nothing outside it was touched.

**3. Install the service and its test tools.** `-e` ("editable") installs a link to the source
directory rather than a copy, so an edit to a `.py` file takes effect on the next run with no reinstall:

```bash
pip install -e .
pip install -r requirements-dev.txt
```

This is enough to run the tests and the linter. **Running the service also needs the platform's API
client**, which is in this repository and not on any index:

```bash
pip install -e ../workflows/pipe-common
```

Expect some forty packages: `pipeline/__init__.py` imports the whole of pipe-common - luigi, tornado,
paramiko - and pip resolves its pins, including its own `requests`. Without it the service stops at
startup with `No module named 'pipeline'`; the tests do not need it, because `sats/api.py` imports it
inside the function that builds the client.

**4. Get a token.** The service needs a JWT of a user with the `ROLE_ADMIN` role, because it reads any
storage any user asked about. In the GUI: **Settings → CLI → Pipe CLI**, press **Generate access key**,
and take the token out of the **CLI configure command** it shows - that command passes it as
`--auth-token`. `jwt-generator/` in this repository mints one as well, if you have the deployment's
signing key. Keep it out of your shell history and out of any file in the repository:

```bash
read -rs API_TOKEN && export API_TOKEN      # type the token, press enter; it is not echoed
export API=https://your-deployment.example.com/pipeline/restapi
```

**5. Run one round and stop:**

```bash
python -m sats.app --once --slots 1 --work-dir ./tasks --log-level DEBUG
```

With `--once` the service does one round - preference, cleanups, claim - waits for whatever it started,
and exits. That is the mode to develop in: a full round against a real API, over in seconds, and every
decision visible at `DEBUG`.

Without `--once` it loops until `Ctrl-C`, which is how it runs in a container:

```bash
python -m sats.app --slots 2 --work-dir ./tasks
```

`Ctrl-C` does not kill it. The signal asks the orchestrator to stop claiming and to wait for the
workers, so an upload in progress is either finished or aborted properly. Pressing it a second time
changes nothing; if you really need the process gone now, `kill -9` it and let the cleaner clean up -
which is exactly the case the cleaner exists for, and a decent way to see it work.

### Pointing it at nothing in particular

The service is useful to run even with no task waiting: one round logs the preference it read, the
warnings about it, and the fact that it found nothing to claim. If the url or the token is wrong you
find out here, in one line, instead of inside a worker.

## End to end: one archive by hand

This is the whole feature without the GUI. Set `API` and `API_TOKEN` as above, and pick a storage id
you can read.

**1. Ask for an archive.** The paths are relative to the root of the storage, and a trailing slash
marks a folder:

```bash
curl -sk -X POST "$API/datastorage/1/download/archive" \
     -H "Authorization: Bearer $API_TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"paths": ["data/", "notes.txt"], "archiveName": "my-selection.zip"}'
```

The answer holds the new task, `"status": "PENDING"`. Note its `id` - a string, not a number.

**2. Run one round of the service:**

```bash
python -m sats.app --once --slots 1 --work-dir ./tasks --log-level DEBUG
```

**3. Look at the task:**

```bash
curl -sk "$API/async-task/<task-id>" -H "Authorization: Bearer $API_TOKEN"
```

`SUCCESS` carries a `result` with the archive's size in bytes and how many files were skipped. `FAILED`
carries an `errorMessage` written for the user who asked - that message is shown in the GUI, which is
why the worker spends effort on it.

**4. Download the archive:**

```bash
curl -sk "$API/async-task/<task-id>/url" -H "Authorization: Bearer $API_TOKEN"
curl -o archive.zip "<the url from the answer>"
```

**5. Cancel one, to see the other half.** Start a task over something large, run the service *without*
`--once`, and in another terminal:

```bash
curl -sk -X POST "$API/async-task/<task-id>/cancel" -H "Authorization: Bearer $API_TOKEN"
```

The task goes to `CANCEL_REQUESTED`, and nothing else happens until the worker notices on its next
status poll - within `--status-poll-seconds`. It then stops where it is, reports `STOPPING`, deletes its
scratch directory, and reports `CANCELLED`. Cancellation is cooperative because a Python thread cannot
be killed from the outside; see [How to read this Python](#how-to-read-this-python).

### Reading an error that is not an error

The platform API answers a business failure with **HTTP 200** and a body of
`{"status": "ERROR", "message": "..."}`. So `curl -f` will not notice. The platform's client reads the
envelope rather than the status code and raises `APIError` for it, without retrying; this service turns
that into `ApiRejected` - a refusal that will never succeed, such as a transition the task model does
not allow - and everything else into `ApiError`, which is worth retrying. See `_refused` in
`sats/api.py`, and the limit noted in the module's docstring: the older methods of that client flatten
both kinds into one, so for the storage, region and preference calls everything looks retryable.

## Settings

Every setting is both a command line option and an environment variable, and the option wins.
`python -m sats.app --help` prints them with their defaults; this table is the same list with the reason
for each default.

The two switches can be turned off as well as on - `--once` / `--no-once`, `--verify-ssl` /
`--no-verify-ssl` - so a variable left behind in a container environment can always be overridden on
the command line.

| Option | Environment | Default | What it is for |
|---|---|---|---|
| `--cp-api-url` | `API` | - | Base url of the API, **including** `/restapi`. Required. |
| `--cp-api-token` | `API_TOKEN` | - | Administrator JWT. Required. Never logged. |
| `--verify-ssl` | `CP_SATS_VERIFY_SSL` | off | Off because a deployment normally signs its own certificate. **Turning it on has no effect**: the platform's API client passes `verify=False` on every call and offers no way to change it, and the service says so once at startup. |
| `--work-dir` | `CP_SATS_WORK_DIR` | `/tmp/sats` | Scratch space, one subdirectory per task. Needs room for the largest archive the thresholds allow, times `--slots`. |
| `--slots` | `CP_SATS_SLOTS` | `2` | Tasks at once. One slot is one thread, and one archive on disk. |
| `--poll-interval-seconds` | `CP_SATS_POLL_INTERVAL` | `20` | Sleep between rounds when nothing was claimed. |
| `--cleanup-interval-seconds` | `CP_SATS_CLEANUP_INTERVAL` | `300` | How often the cleanups run. Apart from the loop, because the loop also claims tasks and has to stay fast, while nothing the cleanups look for appears in seconds - their thresholds are `stuckTasksThresholdMinutes` and `ttlDays`. The first round after a start always cleans up, whatever this says: a restart is when there is most to clean. `0` turns them off, and the service warns about it at startup. |
| `--status-poll-seconds` | `CP_SATS_STATUS_POLL` | `15` | How often a worker asks whether its task is still wanted. The lower bound on how fast a cancellation is noticed. |
| `--part-size-mb` | `CP_SATS_PART_SIZE_MB` | `8` | Upload part size. S3 allows 10000 parts, so this caps one archive at 80 GiB - `archive_bucket.py` raises the part size by itself when a bigger archive needs it. |
| `--compress-level` | `CP_SATS_COMPRESS_LEVEL` | `1` | ZIP deflate level. Low on purpose: research data is usually already compressed, and level 6 would spend minutes of CPU per gigabyte for a few percent. |
| `--nfs-mount-root` | `CP_SATS_NFS_MOUNT_ROOT` | `/mnt/sats-shares` | Where NFS shares are mounted, one subdirectory per share. |
| `--nfs-mount-timeout-seconds` | `CP_SATS_NFS_MOUNT_TIMEOUT` | `30` | Hard limit on `mount` and on the liveness check. A dead NFS server hangs both forever by default, and a hung mount in a worker thread is unkillable. |
| `--once` | `CP_SATS_ONCE` | off | One round, then exit. |
| `--log-level` | `CP_SATS_LOG_LEVEL` | `INFO` | `DEBUG` logs every file considered, which is a great deal of output for a large selection. |

## The platform preference

Everything an administrator can tune lives in the `storage.download.archive.config` preference, read
anew on **every** round - so a change in the UI takes effect without restarting the service. The field
names mirror `DownloadArchiveConfig` on the Java side one for one:

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

- **`destinationStorageId`** is the only required field: the id of the platform data storage the
  archives are written to. Without it no task can run, and the service says so once per round rather
  than claiming tasks it cannot finish. A storage rather than a bare bucket, so that the API signs the
  download link, resolves the region and reaches the right cloud through the machinery it already has
  for every other storage - and so that this service can find the bucket behind it with the same
  `GET /datastorage/{id}/load` it uses for the storage it is reading from.
- **`destinationPrefix`** is the folder inside that storage where the archives go, storage-relative.
  Blank means `archives/`, a lone `/` means the root of the storage. Changing it is safe for a
  **finished** archive: a worker records the storage and the key it actually wrote in the task's
  result, and both the link and the expiry cleanup use the recorded pair as it stands. It is **not** safe
  for one in flight - a worker that uploaded and then died recorded nothing, so its key can only be
  derived from the setting, and after a change the derived key names an object that was never written.
  Deleting an absent key succeeds, so the cleanup reports done while the real object stays where it
  was. Change it when nothing is running.
- **`maxSizeBytes`** and **`maxFilesCount`** are checked by this service, in one pass over the listing,
  *before* a single byte is downloaded. A field that is **absent** falls back to the platform default; a
  field present and set to **`null`** means no limit, and is logged as such - an unlimited archive is
  what the administrator asked for, but worth a line in the log.
- **`ttlDays`** is how long a finished archive is kept.
- **`maxUserTasks`** is enforced by the API at task creation, not here.
- **`workerMaxRunningMinutes`** is when a worker gives up on its own task.
- **`stuckTasksThresholdMinutes`** is when the cleaner gives up on a worker. It has to exceed
  `workerMaxRunningMinutes` by enough for a worker to notice its own deadline, clean up and report -
  otherwise the cleaner fails tasks whose workers are alive and about to finish properly, and the user
  sees a task fail for no visible reason. The service warns at startup if the two are too close.

## Running the tests and the linter

```bash
pytest                 # settings come from setup.cfg
flake8                 # line length 110, as in the rest of the repository
```

The tests touch nothing outside the process: no API, no S3, no network. The stand-ins live in
`tests/fakes.py`, and they are hand-written rather than built with `unittest.mock` on purpose - a mock
answers any call, including a call to a method that no longer exists, so it keeps passing after the
thing it stands for has changed.

One fixture in `conftest.py` is what keeps that promise where S3 clients are built: `boto3` walks the
real credential chain when a session is created, which reads `~/.aws`, honours `AWS_PROFILE` and probes
the instance metadata endpoint on a host with nothing configured. The fixture points all of that at
fake values, so the suite is the same on every machine.

`tmp_path` and `monkeypatch`, which most tests take as parameters, are pytest *fixtures*: naming one as
a parameter is how a test asks for it. `tmp_path` is a fresh directory, deleted afterwards;
`monkeypatch` replaces something for one test and puts it back.

## Troubleshooting

| What you see | What it means |
|---|---|
| `Both the API url and the API token are required`, exit code 22 | Neither the options nor `API`/`API_TOKEN` were set. 22 is `EINVAL`, the same code the other Python services in the platform use for a bad setting. |
| `--slots`, `--compress-level`, `--part-size-mb` or a poll interval rejected, exit code 22 | A setting outside its range. Checked at startup rather than at first use, because a value that arrived through the environment goes through none of `argparse`'s own validation. |
| `Preference storage.download.archive.config has no destinationStorageId` | The preference is missing or has no `destinationStorageId`. The service keeps running and claims nothing; tasks stay `PENDING` until it is filled in. Mind the spelling: the API silently ignores a field it does not know, so a typo leaves the destination unset with no complaint. |
| A task stays `RUNNING` long after `stuckTasksThresholdMinutes`, or an expired archive is still there | The cleanups may be off (`--cleanup-interval-seconds 0`, which is warned about at startup) or simply not due yet - they run every `--cleanup-interval-seconds`, five minutes by default. |
| Tasks stay `PENDING` and the log says nothing was claimed | Either no destination storage (above), or every slot is busy, or the tasks are not of type `DOWNLOAD_ARCHIVE`. |
| `Task ... cannot move to ...` at `INFO` | Normal. The cleaner and a slow worker both tried to finish the same task; the API refused the loser. The task did end properly. |
| A task fails with `more than N files` or `larger than` | A threshold from the preference. The message is the one the user sees. |
| A task fails with `has ... of local space free and needs about ...` | `--work-dir` cannot hold the archive plus the largest single file. Checked before downloading, so it fails in seconds rather than running out of disk mid-pack. |
| Files missing from the archive, and a `README.txt` inside it | Expected, and deliberate: a file that cannot be read costs that file only, not the whole archive. The note names every path the user ticked that did not make it in, and why: an object archived in Glacier and not restored, a file the assumed role may not read, a download that failed, or a path that is not in the storage at all - `nothing found at s3://...`, which names the place that was looked at. If the selection already has a `README.txt` of its own, the note is called `CLOUD-PIPELINE-SKIPPED-FILES.txt` instead. |
| `Removing scratch directory` never fires for a directory you expected it to | The cleanup only touches directories whose name is a task id, so a `--work-dir` shared with something else is safe. Clean the rest up yourself. |
| A task fails with `Cannot read cloud region N of storage X` | The region behind the storage could not be read, so there is no role to assume and no region to sign for. The API's own message is in the line: usually the region was deleted, or this service's token lost its administrator role. |
| A task fails with `Storage X belongs to no cloud region` | A cloud storage with no region record. Nothing in this service can fix it; the storage needs one. |
| `AccessDenied` from boto3, with a role that was assumed | The assumed role cannot do what was asked. Which role it was is in the `Assuming the role ...` line at `DEBUG`. A storage whose objects are encrypted with a KMS key that role cannot use looks the same way. |
| A task fails with `This host has no credentials of its own, so the role ... cannot be assumed` | There is nothing to call STS with: on a node, the instance profile is missing. Nothing is wrong with the task. |
| `AccessDenied` on `sts:AssumeRole` | The host's own credentials exist but that role does not trust them. Its trust policy is the place to look. |
| A task fails with `The cloud region names the AWS profile ...` | The region has a `profile` set, and the host's AWS configuration does not define it. |
| `ExpiredToken` during a long task | Should not happen: the assumed session is re-assumed by botocore when it is close to expiring, inside the signing path. If it does, the re-assume itself failed, and its error is logged above this one. |
| A multipart upload left its parts behind, with `Could not abort multipart upload` above it | The assumed role has no `s3:AbortMultipartUpload`. The archive itself is unaffected; the parts stay until the bucket's lifecycle rule clears incomplete uploads, which such a bucket should have. |
| An NFS task fails with `cannot be mounted` | No `mount` command, no permission for it, or the share is unreachable from where you are running. Expected on a laptop. |
| A task stays `RUNNING` with no log output | Its worker is gone. Nothing to do: the cleaner fails it after `stuckTasksThresholdMinutes`, deletes any archive, and the directory under `--work-dir` goes with it. |
| `Removing scratch directory ...` after a restart | The cleaner collecting after a killed process. Working as intended. |

## The files, and what each one is for

Read them in this order; each one only uses the ones above it.

| File | What is in it |
|---|---|
| `sats/errors.py` | The exception types. The important split is `ApiRejected` (never retry) against `ApiError` (retry); then `StopRequested` with the reason a worker is stopping, and `CredentialsUnavailable` for a storage whose region says nothing about how to reach it. |
| `sats/log.py` | One function that configures logging to stdout, with the thread name in every line - which is how you tell two workers apart. |
| `sats/model.py` | The task, the storage, the file share, the status enum, and the API's `LocalDateTime` wire format. Nothing in here calls anything. |
| `sats/config.py` | `ServiceConfig` (ours, read once) against `StorageDownloadArchiveConfig` (the platform's, read every round). Separate types so the reload boundary is visible. |
| `sats/api.py` | The layer over `pipeline.PipelineAPI`, the platform's own client: two error kinds (a refusal that must not be retried against a failure that may be), this service's model instead of dictionaries, and a second client with one attempt for claiming. |
| `sats/worker/__init__.py` | The registry: which worker runs which task type, and `worker_for`, the only name the orchestrator knows. |
| `sats/worker/base.py` | What a worker has to be: `run()`, and the type it serves. |
| `sats/worker/task_stop_watcher.py` | How any worker learns that it must give up - a cancellation, or its own deadline. |
| `sats/cloud/aws.py` | Where every S3 client comes from: the role resolved from the storage and its region, the assumed session botocore re-assumes by itself, and the region each client signs for. |
| `sats/datastorage_providers/base.py` | What a file source has to be able to do: list and download. |
| `sats/datastorage_providers/s3.py` | Object storages. Paginated listing, the Glacier check that decides a file is unreadable rather than letting the download fail, and the report for a selected path that holds nothing. Takes its client as given. |
| `sats/datastorage_providers/nfs.py` | Mounted shares. Mounts read-only, with a timeout, refuses anything that is not NFS, and reports a selected path that is not on the share. |
| `sats/datastorage_providers/__init__.py` | Picks the source for a storage type. |
| `sats/archive_bucket.py` | Uploading and deleting the archive object. Multipart by hand, so that a failed upload can be aborted instead of leaving billable parts behind. |
| `sats/worker/archive_preparation_worker.py` | One `DOWNLOAD_ARCHIVE` task from start to finish. The table at the top of `run` is the whole contract: every way a task can end, and what is reported for it. |
| `sats/worker/cleaner.py` | The four cleanups that end tasks whose workers are gone, and delete archives past their lifetime. Not a worker: nobody claims a task for it. |
| `sats/orchestrator.py` | The loop: read the preference, cleanup, claim while a slot is free, sleep. Owns the thread pool and the shutdown flag. |
| `sats/app.py` | Arguments, logging, signal handlers. |

## How to read this Python

Written for someone who reads Java. The code sticks to the standard library and leans on a handful of
features that have no direct Java equivalent; these are the ones that will look strange.

**A virtual environment and `pip`** are Maven's local repository and `mvn install`, except the
dependencies land in a directory you can delete (`.venv`) and the versions are pinned in a text file
(`requirements.txt`). There is no compile step: `pip install -e .` registers the source directory, and
`python -m sats.app` runs it.

**A module is a file, a package is a directory** with an `__init__.py` in it. `from .config import
ServiceConfig` is a relative import - the leading dot means "the package this file is in". There is no
`public`/`private`: a leading underscore (`_scan`, `_MOUNT_LOCK`) means "not part of what this file
offers", and nothing enforces it.

**`from __future__ import annotations`**, at the top of most files, makes every type hint a string the
interpreter does not evaluate. It is there so that a hint may name a class defined later in the file,
and so that imports used *only* in hints cost nothing at runtime.

**Type hints are not checked.** `def upload(self, path: Path, key: str) -> int` documents the intent and
drives the IDE; passing a string where a `Path` is declared runs anyway, and fails later or not at all.
`X | None` is "nullable", and it is where most of the thought went: `max_files_count: int | None` has
`None` meaning *no limit*, which is a real value and not a missing one.

**`@dataclass(frozen=True, slots=True)`** generates the constructor, `__eq__` and `__repr__` from the
field list - a Java record. `frozen=True` makes instances immutable, which is what lets a config object
be read by several threads with no locking. `slots=True` forbids attributes that were not declared, so
a typo in an assignment raises instead of quietly creating a new field.

**`StrEnum`** is an enum whose members *are* strings: `TaskStatus.SUCCESS == 'SUCCESS'` is true, and the
JSON encoder serialises it with no help. That is why statuses pass in and out of the API with no
conversion layer.

**`match`** (in `datastorage_providers/__init__.py`) is a switch. The `case _:` at the end is the default, and here it
raises - a storage type nobody has implemented must fail loudly rather than fall through to a wrong
source.

**An f-string** is `f'task {task.id} failed'` - interpolation, like a Java template string. `{value!r}`
inside one asks for the debugging form, which is how a log line can show that a setting was the empty
string rather than absent.

**A context manager**, `with X() as x:`, is try-with-resources. `__enter__` and `__exit__` are the two
methods behind it, and `__exit__` runs however the block is left - return, exception, anything. Every
file, every ZIP, every source in this service is opened in a `with`, because a worker that stops
mid-task has to release what it holds.

**A generator** is a function with `yield` in it: calling it runs nothing, and the body advances one
item at a time as the caller iterates. `list_files` is one. The point is that a selection of a hundred
thousand files is never a list in memory - the worker sees one file at a time, and the listing calls to
S3 are paged behind the same iteration.

**`:=`**, the walrus, assigns inside an expression. Every use of it here is the same read loop:
`while chunk := body.read(part_size):` reads the next block, binds it, and stops when the read comes
back empty - one line instead of a read before the loop and another at the bottom of it.

**A closure** is a function that remembers the scope it was defined in; `_default_bucket_factory`
returns one. The trap, and it is a real one here: a closure captures the *variable*, not its value, so
a lambda written in a loop sees the variable's last value. The orchestrator's
`lambda _, task_id=task.id: ...` sidesteps it by making the value a default argument, which is
evaluated once, at definition.

**Exceptions are used as control flow** on purpose. `TaskStopWatcher.check()` raises `StopRequested` the
moment the task was cancelled or the deadline passed; every loop that copies or uploads calls it
between blocks. That is how stopping works at all: **a Python thread cannot be killed from the
outside**, so a worker has to agree to stop, and this is where it agrees.

**`threading`**: an `Event` is a flag one thread sets and another waits on, and `event.wait(20)` is a
sleep that a shutdown can cut short - which is why the loop never calls `time.sleep`. A
`BoundedSemaphore` is the slot count, bounded because it raises on a release too many instead of
quietly granting an extra slot. A `ThreadPoolExecutor` runs the workers and hands back a `Future` per
task; `add_done_callback` is how a slot is released no matter how the task ended. And
`threading.local()`, in `api.py`, is an object whose attributes are per thread: it is how each thread
gets its own HTTP session without a shared lock that one slow request could hold for everybody.

**The GIL**, Python's global interpreter lock, means threads do not run Python code in parallel. It
sounds fatal and is not: this service spends its time waiting on sockets and disks, and the lock is
released during every one of those waits. Threads are the right tool here for the reason they are the
wrong tool for arithmetic.

**`pathlib`**: `Path('/tmp') / 'sats' / 'archive.zip'` builds a path with `/`, and `Path` carries
`exists()`, `mkdir()`, `read_bytes()`, `rglob()` with it. `is_relative_to` is the one that matters for
safety - in `datastorage_providers/nfs.py` it is how a listing refuses a path that resolves outside the storage's own
folder, a symbolic link being the usual way that happens.

**`subprocess.run(..., timeout=...)`** runs an external command. The timeout is not decoration: a
`mount` against a dead NFS server never returns, and without it a worker thread would hang for the life
of the process.
