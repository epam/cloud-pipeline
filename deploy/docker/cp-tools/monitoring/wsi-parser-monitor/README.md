# Tool description
This tool allows monitoring state and analyzing logs of WSI-parser run instances.

## Configuration parameters

Notification parameters:

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_SERVICE_MONITOR_NOTIFICATION_USER          | Required | USER1                     | The user who will receive notifications |
| CP_SERVICE_MONITOR_NOTIFICATION_TEMPLATE_PATH | Required | USER1                     | Path to the file with the default notification template |
| CP_SERVICE_MONITOR_NOTIFICATION_COPY_USERS    | Optional | USER2,USER3               | Comma-separated list of users who will be added as a CC in the notification email |
| CP_SERVICE_MONITOR_NOTIFICATION_SUBJECT       | Optional | DTS statuses were changed | Subject of the message. If not specified, the default value will be used |

WSI-parser monitoring parameters

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_WSI_MONITOR_TARGET_IMAGE         | Required | <CP_deployment_URL>/library/wsi-parser:latest<br> | WSI-parser image pattern, that will be used to determine target runs for analysis<br>_Wildcard could be used to match all the versions: ..library/wsi-parser:\*_ |
| CP_WSI_MONITOR_LAST_SYNC_TIME_FILE  | Required | /cloud-data/storage/wsi_monitor_last_sync         | Path to a file, that contains timestamp of the last synchronization, while searching for matching WSI runs |
| CP_WSI_MONITOR_PATTERNS_FILE_PATH   | Optional | /cloud-data/storage/log_search_patterns.txt       | Path to a file, that contains search regexes (each regex should be placed on the new line) |
| CP_WSI_MONITOR_TARGET_TASKS         | Optional | InputData,WSI processing,OutputData               | Comma-separated list of run's task names, that are going to be analyzed against patterns, specified in __CP_WSI_MONITOR_PATTERNS_FILE_PATH__ <br>*__WSI processing__* by default |
| CP_WSI_MONITOR_RUN_SEARCH_PAGE_SIZE | Optional | 100 (any integer number)                          | Define paging configuration while searching for matching WSI runs  |
