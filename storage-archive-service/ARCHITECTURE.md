# A map of the service

One page to orient by: what runs, when it runs, which file it lives in, and which setting it consults at
that moment. The README explains *why* each thing is the way it is; this file only says *where* it is.

Nothing here is a second source of truth about behaviour - every box names the function to read.

## Contents

- [The layout](#the-layout)
- [The loop, and what one round does](#the-loop-and-what-one-round-does)
- [One task, start to finish](#one-task-start-to-finish)
- [Threads](#threads)
- [What is scheduled, and by what](#what-is-scheduled-and-by-what)
- [Settings: the platform preference](#settings-the-platform-preference)
- [Settings: this process](#settings-this-process)
- [Constants that are not settings](#constants-that-are-not-settings)
- [Every API call this service makes](#every-api-call-this-service-makes)
- [Where to look first](#where-to-look-first)

## The layout

```
 sas/
   app.py                       flags, environment, signals
   orchestrator.py              the loop: read the preference, clean up, claim, sleep
   api.py                       the layer over pipeline.PipelineAPI
   config.py                    ServiceConfig (ours) and StorageDownloadArchiveConfig (the platform's)
   model.py                     the wire format, parsed into frozen types
   errors.py                    what this service raises at itself, on purpose
   log.py                       logging to stdout, with the thread name in every line
   archive_bucket.py            the object key, and the upload of the finished ZIP
   cloud/
     aws.py                     which role is assumed, and the S3 client that comes out of it
   datastorage_providers/
     base.py                    what a provider has to do: list and download
     s3.py                      object storages
     nfs.py                     mounted shares
     __init__.py                picks the provider for a storage type
   worker/
     base.py                    what a worker has to be
     archive_preparation_worker.py   DOWNLOAD_ARCHIVE, start to finish
     task_stop_watcher.py       how a worker learns it must give up
     cleaner.py                 the four cleanups; not a worker
     __init__.py                task type -> worker, and `worker_for`
```

## The loop, and what one round does

```
 app.py  main()
   |  parses flags and environment  -> ServiceConfig          config.py  ServiceConfig
   |  installs SIGINT/SIGTERM       -> orchestrator.stop()    app.py     _install_signals
   v
 orchestrator.py  Orchestrator.run()                  [MainThread]
   |
   |   ThreadPoolExecutor(max_workers = --slots)
   |
   +-> one round:  orchestrator.py  _round()
   |     |
   |     | 1. read the preference            GET preferences/storage.download.archive.config
   |     |    orchestrator.py  _load_archive_config()  ->  config.py  StorageDownloadArchiveConfig.from_preference
   |     |    unreadable or unparsable -> skip this round entirely, nothing is claimed
   |     |    no destinationStorageId  -> warn once, claim nothing     config.py  is_usable
   |     |
   |     | 2. four cleaner cleanups            cleaner.py  Cleaner.cleanup()
   |     |      1  CANCEL_REQUESTED stuck -> STOPPING -> CANCELLED   _finish_unconfirmed_cancellations
   |     |      2  RUNNING/STOPPING stuck -> FAILED                  _fail_stuck_tasks
   |     |      3  SUCCESS past ttlDays   -> EXPIRED                 _expire_finished_tasks
   |     |      4  scratch directories nobody owns -> deleted        _remove_orphan_directories
   |     |      cleanups 1-3: POST async-task/filter, one each.  cleanup 4: local disk only
   |     |      one cleanup failing costs only itself
   |     |
   |     | 3. claim while a slot is free     orchestrator.py  _claim_while_free()
   |     |      take a slot (BoundedSemaphore, non-blocking)
   |     |      POST async-task/next?type=<each of SUPPORTED_TASK_TYPES in turn>
   |     |         never retried - the claim goes through api.py's single-attempt client
   |     |      empty queue -> give the slot back, end the round
   |     |      got a task  -> worker_for(task.type) and hand it to the pool
   |     v
   |   --once  -> leave the loop, wait for the workers, exit
   |   else    -> self._shutdown.wait(--poll-interval-seconds), then the next round
   v
 "Stopped"
```

The preference is read **once per round** and handed to the cleanups and to each worker started in that
round. So an administrator's change takes effect on the next round, and a worker already running keeps
the settings it was started with - including its own deadline.

## One task, start to finish

Everything below happens on one worker thread, named `task-<first 8 of the id>`.

```
 worker.py  ArchivePreparationWorker.run()            never raises; every path ends in a status or an explained silence
   |
   | _execute()
   |
   | 1. the task must name a storage and paths           -> InvalidTask
   | 2. mkdir work_dir/<task id>
   |
   | 3. _resolve_destination()
   |      GET datastorage/{destinationStorageId}/load            <- preference destinationStorageId
   |      must be type S3                                        -> InvalidTask
   |      cloud/aws.py  build_s3_client()
   |         GET cloud/region/{storage.regionId}
   |         resolve_role_arn()   useAssumedCredentials ? storage/region tempCredentialsRole
   |                              : region.iamRole, else region.tempCredentialsRole
   |         sts:AssumeRole, refreshed by botocore itself
   |      key = destinationPrefix + task id + ".zip"            <- preference destinationPrefix
   |            archive_bucket.py  archive_key(), then absolute_key() with the storage's own folder
   |
   | 4. _open_source()
   |      GET datastorage/{task.storageId}/load
   |      datastorage_providers/__init__.py  open_source()
   |         S3  -> cloud/aws.py  build_s3_client() (as above)          datastorage_providers/s3.py   S3Source
   |         NFS -> GET filesharemount/{id}, mount read-only     datastorage_providers/nfs.py  NfsSource
   |         anything else                                       -> UnsupportedSource
   |
   | 5. _scan()                                           one pass over the listing, nothing downloaded
   |      datastorage_providers/*.py  list_files()   folders expanded, one at a time (a generator)
   |      readable files counted against   maxFilesCount         <- preference
   |                                       maxSizeBytes          <- preference
   |      unreadable ones collected with a reason instead:
   |         Glacier and not restored, access denied, nothing found at that path
   |      _assert_fits_locally()   free space of --work-dir  >=  selection + largest single file
   |
   | 6. _pack()                                           work_dir/<task id>/archive.zip
   |      per file:  source.download() into current.part  (1 MiB blocks, heartbeat between them)
   |                 archive.write(current.part)          deflate level --compress-level
   |                 delete current.part
   |      a file that fails here is skipped and noted, not fatal
   |      a note is added if anything was skipped:  README.txt, or
   |      CLOUD-PIPELINE-SKIPPED-FILES.txt if the selection has a README.txt of its own
   |
   | 7. _upload()                      archive_bucket.py  ArchiveBucket.upload()
   |      <= one part  -> put_object
   |      otherwise    -> create/upload/complete multipart, part size --part-size-mb
   |                      parts are sequential, heartbeat between them, aborted on any failure
   |
   | 8. _finish_succeeded()
   |      POST async-task/{id}/status  SUCCESS
   |      result: storageId, path (storage-relative), archiveSizeBytes, skippedFiles
   |
   +-- finally: delete work_dir/<task id>, log how long the task held its slot
```

Every other ending, from the table at the top of `worker/archive_preparation_worker.py`:

| What happened | Reported | Archive |
|---|---|---|
| cancel noticed on a poll | `STOPPING` then `CANCELLED` | deleted |
| own deadline passed (`workerMaxRunningMinutes`) | `STOPPING` then `FAILED` | deleted |
| task already finished elsewhere | nothing - nothing would be accepted | deleted |
| a threshold breached | `FAILED`, the limit named | never built |
| storage type unsupported, destination not S3, no region | `FAILED`, the reason named | never built |
| the API refused a mid-flight call | nothing | deleted |
| anything else | `FAILED` as an internal error | deleted |

## Threads

```
 MainThread          app.py -> orchestrator.run(): rounds, cleaner cleanups, claiming
                     also runs the signal handlers (they only set an Event)
 worker-0..N-1       the pool threads, renamed to task-<id8> while they hold a task
                     N = --slots; each builds its own S3 clients and its own credentials
```

Shared state between them: the `CloudPipelineApi` instance - which holds no state of its own, because
the platform's client makes a fresh request every time - the slot semaphore, and the set of active task
ids. The cleaner's caches are only ever touched from `MainThread`.

## What is scheduled, and by what

There is no cron and no timer thread. Everything is one of three clocks:

| What | How often | Controlled by | Where |
|---|---|---|---|
| a round of the loop | after each round, if nothing stopped it | `--poll-interval-seconds` (`CP_SAS_POLL_INTERVAL`, 20 s) | `orchestrator.py run()`, via `Event.wait` so a shutdown cuts it short |
| the four cleanups | once per round | nothing of their own | `worker/cleaner.py cleanup()` |
| "is my task still wanted?" | per worker, between files and between upload parts | `--status-poll-seconds` (`CP_SAS_STATUS_POLL`, 15 s) | `worker/task_stop_watcher.py TaskStopWatcher.check()`; the first poll is due one interval after the task was taken |
| "have I run too long?" | the same check, no API call | preference `workerMaxRunningMinutes` | `worker/task_stop_watcher.py TaskStopWatcher`, deadline fixed when the worker starts |
| a task counts as stuck | measured on the API's clock, not ours | preference `stuckTasksThresholdMinutes` | `worker/cleaner.py _stuck_before()` |
| an archive counts as expired | measured on the API's clock | preference `ttlDays` | `worker/cleaner.py _expire_finished_tasks()` |
| a scratch directory counts as abandoned | its mtime, plus being unknown to every live worker | `ORPHAN_GRACE_MINUTES`, a constant | `worker/cleaner.py _remove_orphan_directories()` |

With `--once` there is exactly one round, and the process waits for whatever that round started. That
makes a cron-style deployment possible, and it is the mode to develop in.

## Settings: the platform preference

`storage.download.archive.config`, read every round, parsed by `config.py StorageDownloadArchiveConfig.from_preference`.
Field names mirror `DownloadArchiveConfig` on the Java side one for one.

| Field | Default when absent | Used when | Used in |
|---|---|---|---|
| `destinationStorageId` | none - **required** | claiming (nothing is claimed without it); resolving the destination; the cleaner's fallback key | `config.py is_usable`, `worker/archive_preparation_worker.py _resolve_destination`, `worker/cleaner.py _delete_archive` |
| `destinationPrefix` | `archives/` | building the object key, at worker construction | `archive_bucket.py archive_key`, `config.py normalise_prefix` |
| `maxSizeBytes` | 100 GiB; `null` means no limit | during the listing pass, before any download | `worker/archive_preparation_worker.py _assert_within_thresholds` |
| `maxFilesCount` | 100000; `null` means no limit | the same pass, per file | `worker/archive_preparation_worker.py _assert_within_thresholds` |
| `ttlDays` | 7 | cleanup 3 | `worker/cleaner.py _expire_finished_tasks` |
| `workerMaxRunningMinutes` | 240 | fixed into the worker's deadline when it starts | `worker/task_stop_watcher.py TaskStopWatcher` |
| `stuckTasksThresholdMinutes` | 360 | cleanups 1 and 2 | `worker/cleaner.py _stuck_before` |
| `maxUserTasks` | 3 | **never here** - the API enforces it when the task is created | parsed only, for the startup warnings |

`StorageDownloadArchiveConfig.warnings` is what complains at startup about a `stuckTasksThresholdMinutes` too close to
`workerMaxRunningMinutes`, and about a threshold explicitly set to "no limit".

Two platform preferences this service deliberately does *not* read:

- `storage.temp.credentials.duration` - it mattered when credentials were fetched from the API. The
  service assumes a role instead, and botocore re-assumes on its own schedule.
- anything about the GUI or the download link - the API signs the link, not this service.

## Settings: this process

Read once at startup; every one is both a flag and an environment variable, and the flag wins. See
`app.py` for the parsing and `config.py ServiceConfig` for the meaning.

| Flag | Environment | Default | Consulted in |
|---|---|---|---|
| `--cp-api-url` | `API` | required | `api.py` |
| `--cp-api-token` | `API_TOKEN` | required | `api.py` (never logged) |
| `--verify-ssl` | `CP_SAS_VERIFY_SSL` | off | nowhere: the platform's API client cannot honour it, and `api.py` warns once at startup if it is on |
| `--work-dir` | `CP_SAS_WORK_DIR` | `/tmp/sas` | `worker/archive_preparation_worker.py` (one subdirectory per task), `worker/cleaner.py` cleanup 4, `worker/archive_preparation_worker.py _assert_fits_locally` |
| `--slots` | `CP_SAS_SLOTS` | 2 | `orchestrator.py` pool size and semaphore |
| `--poll-interval-seconds` | `CP_SAS_POLL_INTERVAL` | 20 | `orchestrator.py run()` |
| `--status-poll-seconds` | `CP_SAS_STATUS_POLL` | 15 | `worker/task_stop_watcher.py TaskStopWatcher` |
| `--part-size-mb` | `CP_SAS_PART_SIZE_MB` | 8 | `archive_bucket.py` upload |
| `--compress-level` | `CP_SAS_COMPRESS_LEVEL` | 1 | `worker/archive_preparation_worker.py _pack` |
| `--nfs-mount-root` | `CP_SAS_NFS_MOUNT_ROOT` | `/mnt/sas-shares` | `datastorage_providers/nfs.py` |
| `--nfs-mount-timeout-seconds` | `CP_SAS_NFS_MOUNT_TIMEOUT` | 30 | `datastorage_providers/nfs.py` mount and liveness check |
| `--once` | `CP_SAS_ONCE` | off | `orchestrator.py run()` |
| `--log-level` | `CP_SAS_LOG_LEVEL` | `INFO` | `log.py` |

Cloud credentials are not among these, by design: the role comes from the storage's region and is
assumed with whatever the host has - on a node, its instance profile. See `cloud/aws.py`.

## Constants that are not settings

Nobody can tune these; they are here so that a number seen in the code can be found.

| Constant | Value | Where | Why fixed |
|---|---|---|---|
| `AsyncTaskType` | `DOWNLOAD_ARCHIVE` | `model.py` | the task types this service claims; the workers are registered by it |
| `DOWNLOAD_BLOCK_BYTES` | 1 MiB | `datastorage_providers/base.py` | the granularity at which a cancellation is noticed inside one file |
| `LIST_PAGE_SIZE` | 1000 | `datastorage_providers/s3.py` | S3's own maximum and default |
| `MAX_PARTS` / `MIN_PART_SIZE_BYTES` | 10000 / 5 MiB | `archive_bucket.py` | limits of S3, not choices |
| `CLEANUP_PAGE_SIZE` | 200 | `worker/cleaner.py` | how much of a backlog one round works through |
| `ORPHAN_GRACE_MINUTES` | 30 | `worker/cleaner.py` | margin against a directory created a moment before the cleanup |
| `ROLE_SESSION_NAME` | `CLOUD_PIPELINE_SESSION` | `cloud/aws.py` | matches `AWSUtils.ROLE_SESSION_NAME`, so CloudTrail shows the platform |
| `ARCHIVE_EXTENSION` | `.zip` | `archive_bucket.py` | the API derives the same key |

## Every API call this service makes

| Call | Who | When |
|---|---|---|
| `GET preferences/storage.download.archive.config` | `MainThread` | once per round, first thing |
| `POST async-task/filter` | `MainThread` | three times per round, cleanups 1-3 |
| `POST async-task/next?type=DOWNLOAD_ARCHIVE` | `MainThread` | per free slot, while the queue is not empty. Not retried |
| `GET async-task/{id}` | worker | every `--status-poll-seconds` while a task runs; also cleaner cleanup 4 per candidate directory |
| `POST async-task/{id}/status` | worker, and the cleaner for a task it is finishing | on every status change |
| `GET datastorage/{id}/load` | worker | twice per task - the destination and the source; cleaner once per destination storage per process |
| `GET filesharemount/{id}` | worker | NFS storages only |
| `GET cloud/region/{id}` | worker | once per S3 client, so twice per task; cleaner once per destination storage |

Every one of them goes through `pipeline.PipelineAPI` from `workflows/pipe-common`; `sas/api.py` adds
only the two error kinds and the parsing. The four async-task methods were added to that client for this
service, next to the storage, region and preference methods it already had.

No inbound port, no health endpoint, no queue of its own. The API is the only state this service has.

## Where to look first

| Question | File |
|---|---|
| why did nothing get claimed | `orchestrator.py _round`, then `config.py is_usable` |
| why did this task fail with that message | the table at the top of `worker/archive_preparation_worker.py`, then `errors.py` |
| which role am I reaching this storage with | `cloud/aws.py resolve_role_arn`, and the `Assuming the role ...` line at `DEBUG` |
| why is a file missing from an archive | `datastorage_providers/s3.py _expand_folder` and `_to_source_file`, `worker/archive_preparation_worker.py _pack` |
| who deleted an archive | `worker/cleaner.py _delete_archive` and the cleanup that called it |
| why is a task stuck in `RUNNING` | `worker/cleaner.py _fail_stuck_tasks`, and `stuckTasksThresholdMinutes` |
| what is in `--work-dir` right now | `worker/archive_preparation_worker.py` naming, `worker/cleaner.py _remove_orphan_directories` |
