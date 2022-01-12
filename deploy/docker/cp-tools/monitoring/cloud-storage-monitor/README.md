# Tool description
This tool allows monitoring a cloud storage and notify user if file updates occurred since the last sync.

## Configuration parameters

Notification parameters:

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_SERVICE_MONITOR_NOTIFICATION_USER          | Required | USER1                            | The user who will receive notifications |
| CP_SERVICE_MONITOR_NOTIFICATION_TEMPLATE_PATH | Required | /cloud-data/config/template.html | Path to the file with the default notification template |
| CP_SERVICE_MONITOR_NOTIFICATION_COPY_USERS    | Optional | USER2,USER3                      | Comma-separated list of users who will be added as a CC in the notification email |
| CP_SERVICE_MONITOR_NOTIFICATION_SUBJECT       | Optional | Cloud storage updates detected   | Subject of the message. If not specified, the default value will be used |

WSI-parser monitoring parameters

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_CLOUD_STORAGE_MONITOR_TARGET_PATHS | Required | /cloud-data/storage1,/cloud-data/storage2     | Comma-separated list of mounted paths, that are going to be analyzed |
| CP_CLOUD_STORAGE_MONITOR_IGNORE_GLOBS | Optional | \*\*/some-folder/\*\*,\*\*/\*.logs            | Comma-separated list of globs, describing paths that should be ignored during analyzing |
| CP_STORAGE_MONITOR_LAST_SYNC_TIME_FILE| Optional | /cloud-data/storage/storage_monitor_last_sync | Path to a file, that contains timestamp of the last synchronization |
