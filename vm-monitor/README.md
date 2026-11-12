# VM monitoring service

`VMMonitor` is a service for tracking virtual machines in the Cloud provider and verifying that VMs are monitored correctly by Cloud Pipeline API.

### Implementation details 
Service is developed as Spring Boot application with a scheduled task which is invoked according to cron expression. 

The following algorithm is used to check VM and Nodes status:
-   Fetch registered `Cloud regions` from Cloud Pipeline API
-   Fetch running VMs corresponding to each `Cloud regions`
-   For each VM check that corresponding `Node` is registered in Cloud Pipeline k8s cluster (VM and node are matched by private IP address)
-   Additionally service checks if VM is associated with any `PipelineRun` via `Name` tag (configurable), if corresponding `PipelineRun` exists and it is in `RUNNING` status, VM is considered healthy. This check is added mostly for cases when VM is created, but not yet registered in k8s cluster during `NodeInitialization` task.
-   If matching `Node` is not found `MISSING-NODE` notification is sent
-   If matching `Node` exists, service checks that labels required for node management are present (`runid` label by default)
-   If some of required labels is absent, `MISSING-LABELS` notification is sent

### Certificate expiration

The service also checks the certificate files of the platform and notifies about the certificates that expire soon.

It scans the folders of `monitor.cert.scan.folders` and their subfolders. A file is a certificate file if its name
ends with one of `monitor.cert.file.masks`. A certificate is reported if it expires within
`monitor.cert.expiration.notification.days` days, or has already expired. The notification is the
`CERTIFICATE-EXPIRATION` email: its subject is `[<name>] Certificate expires soon`, and it names the certificate's
serial number, subject, issuer, expiration date, SAN and file path. `<name>` is `cloud.pipeline.deployment.name`
(`CP_DEPLOYMENT_ID`), or `cloud.pipeline.platform.name` if that is not set, and `notification.subject.prefix`
(`CP_VM_MONITOR_SUBJECT_PREFIX`), if it is set, comes before the subject. The email is sent to `notification.to-user`,
with copies to `notification.copy-users`.

There are two scans:

- **The full scan** checks every certificate file. A certificate that expires soon is reported by every full scan,
  until it is replaced or removed.
- **The changes check** checks only the files that are new or changed since the last scan of their folder. So a
  certificate that is added close to its expiration is reported at the next changes check (every minute by default),
  not at the next full scan. The checks of the service run one after another, on one thread, so a check can start
  later while another one runs.
  - A file is changed when its modification time, size or file key (the inode on Linux) is different. So a file
    that is replaced by another file with the same modification time is checked too. The content is not compared:
    any rewrite or `touch` of a certificate file that expires soon sends another notification, and a file that is
    overwritten in place with the same modification time and size is not checked until the next full scan.
  - If the check of a file fails, e.g. the notification cannot be formed, the error is logged:
    `Failed to check new or changed certificate file <path>: ...`. The other files are still checked, and the file
    is not checked again until it changes.
  - The first scan of a folder after the service starts only records its files. So a restart sends no notifications.
    The full scan records the files too, so the changes check does not check them again.
  - A file that is removed is forgotten. If it is added again, it is a new file.
  - A folder that does not exist yet is recorded at its first scan after it appears: the changes check does not
    check the files it has then, only the ones added later.
  - If a folder cannot be read, the files that were recorded before are kept, so they are not reported as new when
    the folder can be read again.
  - If the attributes of a certificate file cannot be read, e.g. a symbolic link in a loop, only that file keeps what
    was recorded about it. If it cannot be read at the first scan, it is recorded when it can be read, and not
    checked then: it was there before. A new file is checked once it can be read. A symbolic link whose target does
    not exist is not a file: when the target appears, the link is a new file.
  - Only the full scan logs a folder that cannot be read as an error: `Error during <folder> scanning: ...`. The
    changes check runs every minute, so it logs that at debug level, and it is not in `error.log`. A folder of the
    list may not be in the pod at all: the deployment does not mount `CP_IDP_CERT_DIR` (`/opt/idp/pki`) into it.

| Property | Deployment variable | Default in the deployment | |
|---|---|---|---|
| `monitor.cert.scan.folders` | `CP_VM_MONITOR_PKI_PATH`, `CP_SHARE_SRV_CERT_DIR`, `CP_EDGE_CERT_DIR`, `CP_DOCKER_CERT_DIR`, `CP_IDP_CERT_DIR`, `CP_GITLAB_CERT_DIR`, `CP_API_SRV_CERT_DIR`, `CP_COMMON_CERT_DIR`, `CP_VM_KUBE_PKI_PATH` | the platform's PKI folders, e.g. `/opt/common/pki`, and `/etc/kubernetes/pki` | the folders to scan |
| `monitor.cert.file.masks` | `CP_VM_MONITOR_PKI_MASKS` | `pem,crt,cer` | the endings of the certificate file names |
| `monitor.cert.expiration.notification.days` | `CP_VM_MONITOR_PKI_EXPIRATION_DAYS` | `7` (set by the installer) | how many days before its expiration a certificate is reported |
| `monitor.cert.schedule.cron` | `CP_VM_MONITOR_PKI_MONITORING_CRON` | `0 0 0 ? * *`: every day at 00:00, in the time zone of the service | the schedule of the full scan |
| `monitor.cert.changes.cron` | `CP_VM_MONITOR_PKI_CHANGES_CRON` | `0 * * ? * *`: every minute | the schedule of the changes check |
| `monitor.cert.changes.enable` | `CP_VM_MONITOR_PKI_CHANGES_ENABLE` | `true` | `false` turns the changes check off |

In a deployment, the service logs to `/opt/vm-monitor/logs/vm_monitor.log`, which is mounted from the same folder of
its node. A new file is started every day. For the certificates, it logs:

- `Checking new or changed certificate file <path>` - the changes check found a new or changed file.
- `Sending <certificate subject> certificate expiration notification` - the certificate expires soon, and the
  notification is sent to the API, which queues the email.
- `Successfully sent notification using API client` - the API has queued the email. The notifier (`cp-notifier`)
  then sends it, and logs that in its own log.
- `An error occurred during sending notification using API client` - the API did not accept it.
- `Starting PKI certificates monitoring...` and `Finished PKI certificates monitoring.` - the full scan.
