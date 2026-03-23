# Home Storage Creator

This component creates Cloud Pipeline **home storages** (file share and/or object storage) for users who have no default storage, assigns them as the user's default storage and sets permissions on the share.

---
## Building the image

From the repository root:

```bash
docker build -t <registry>/lifescience/cloud-pipeline:home-dirs-<version> \
  -f deploy/docker/cp-home-dirs-creator/Dockerfile \
  deploy/docker/cp-home-dirs-creator
```
The image entrypoint runs `/create_home_dirs.sh` (packaged in the image build).

---

## Kubernetes (CronJob)

The manifest `deploy/contents/k8s/cp-home-creator/cp-home-creator-dpl.yaml` defines a **CronJob** that runs **once per hour** (`schedule: "0 * * * *"`).

- **Configuration:** environment variables are loaded from the ConfigMap `cp-config-global` (`envFrom`).

Apply:

```bash
kubectl apply -f deploy/contents/k8s/cp-home-creator/cp-home-creator-dpl.yaml
```

## Parameters

Variables are read from the environment (via `cp-config-global`). Names below match the script.

### Required

| Parameter                            | Description                                                 |
|--------------------------------------|-------------------------------------------------------------|
| **API_EXTERNAL** (**API**)           | Cloud Pipeline REST API base URL.                           |
| **CP_API_JWT_ADMIN** (**API_TOKEN**) | JWT with rights to create storages and update users.        |
| **CP_HOME_DIRS_ID_ROOT_FOLDER**      | Library folder ID root where new home storages are created. |

### Optional (general)

| Parameter | Default | Description                                                                                                                                                       |
|-----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **CP_HOME_DIRS_SERVICE_ACCOUNTS** | `""` | Comma-separated user IDs to skip (service accounts).                                                                                                              |
| **CP_HOME_DIRS_SERVICE_MOUNT_CHMOD** | `755` | `chmod` applied on the NFS-mounted home directory tree after creation.                                                                                            |
| **CP_HOME_DIRS_FS_HOME_STORAGE_ENABLE** | `true` | If `true`, file-share home flow is used when file share settings are present; must be paired with `CP_HOME_DIRS_ID_FILE_SHARE` and `CP_HOME_DIRS_ADDR_FILE_SHARE`. |
| **CP_HOME_DIRS_FS_HOME_STORAGE_PREFIX** | `""` | Prefix for file/object storage names/paths, e.g. `'HOME.'`                                                                                                         |
| **CP_HOME_DIRS_CREATE_OBJECT_STORAGE** | `false` | If `true`, also creates object storage for the user.                                                                                                              |
| **CP_HOME_DIRS_STORAGE_OBJECT_TYPE** | `S3` | Object storage type label used in naming.                                                                                                                         |
| **CP_HOME_DIRS_CREATE_BASHRC** | `false` | If `true`, seeds `.bashrc` from `/etc/skel` on the NFS home when missing.                                                                                         |

### Optional (FS quotas)

| Parameter | Default | Description |
|-----------|---------|-------------|
| **CP_HOME_DIRS_APPLY_FS_QUOTAS** | `false` | If `true`, applies FS quota notification thresholds on default storages. |
| **CP_HOME_DIRS_FS_QUOTAS_VOLUME_THRESHOLD_GB_DISABLE_MOUNT** | `250` | Threshold (GB) for disable-mount notification. |
| **CP_HOME_DIRS_FS_QUOTAS_VOLUME_THRESHOLD_GB_READ_ONLY** | `300` | Threshold (GB) for read-only notification. |

### File share (NFS / EFS)

When file home storage is enabled, **both** are required:

| Parameter | Description |
|-----------|-------------|
| **CP_HOME_DIRS_ID_FILE_SHARE** | File share mount ID in Cloud Pipeline. |
| **CP_HOME_DIRS_ADDR_FILE_SHARE** | NFS server and export root (e.g. `fs-xxxx.efs.region.amazonaws.com:/home`). Per-user path is `CP_HOME_DIRS_ADDR_FILE_SHARE/${user_name}`. |

---

## Overview

1. Loads users without `defaultStorageId`.
2. Skips IDs listed in `CP_HOME_DIRS_SERVICE_ACCOUNTS`.
3. **File share path:** creates or finds the FILE_SHARE datastorage via API, grants ownership, sets default storage, updates chmod metadata, then **mounts** `CP_HOME_DIRS_ADDR_FILE_SHARE/<user>` and optional prepares the directory structure (optional `.bashrc`).
5. **Object storage path:** if `CP_HOME_DIRS_CREATE_OBJECT_STORAGE=true`, creates object storage as configured.
6. If `CP_HOME_DIRS_APPLY_FS_QUOTAS=true`, applies quota notification settings where applicable.
