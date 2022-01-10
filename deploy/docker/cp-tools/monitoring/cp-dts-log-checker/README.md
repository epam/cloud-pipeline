## This is a script for checking DTS log files and sending notifications if they has errors.
### Prerequisites
* Variables required for the script to work:

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_DTS_LOG_NOTIFICATION_USER          | Is required | user1                           | The user who will receive notifications if logs have errors |
| CP_DTS_LOG_CHECKER_SYSTEM_DIR         | Is required | /cloud-data/dts-system-dir/     | The full path to the local directory where the last_record.txt file with the previous script state will be saved |
| CP_DTS_LOGS_DIR                       | Is required | /cloud-data/dts-logs-dir/       | The full path to the local directory where DTS logs files stored |
| CP_DTS_LOGS_FILES                     | Optional    | dts*.*                          | The mask of the DTS log files, if not specified, the default file mask will be used |
| CP_DTS_LOG_NOTIFICATION_USERS_COPY    | Optional    | user2, user3                    | Users who will be added as a CC in the notification email |
| CP_DTS_LOG_NOTIFICATION_SUBJECT       | Optional    | DTS logs files has errors       | Subject of the message. If not specified, the default theme will be used |
| CP_DTS_LOG_MESSAGE_PATTERN            | Optional    | r'/*ERROR./*'                   | The pattern by which logs are scanned. If not specified, the default pattern will be used |
| CP_DTS_LOG_URL_TEMPLATE               | Optional    | https://aws.cloud-pipeline.com/pipeline/restapi/datastorage/9606/downloadRedirect?path= | Link for quick access to files with errors from the email |

If you want to change the body of the message, you need to put the template.html file in the same folder where the last_record.txt file will be located.