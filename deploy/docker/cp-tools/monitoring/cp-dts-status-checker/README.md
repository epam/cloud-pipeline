## This is a script for checking DTS statuses and sending notifications if they have been changed.
### Prerequisites
* Variables required for the script to work:

| Env | Requirements | Example value | Description |
| --- | --- | --- | --- |
| CP_DTS_STATUS_NOTIFICATION_USER       | Is required | user1                           | The user who will receive notifications of DTS status change |
| CP_DTS_STATUS_CHECKER_SYSTEM_DIR      | Is required | /cloud-data/dts-status-storage/ | The full path to the local directory where the data.json file with the previous DTS state will be saved |
| CP_DTS_STATUS_NOTIFICATION_USERS_COPY | Optional    | user2, user3                    | Users who will be added as a CC in the notification email |
| CP_DTS_STATUS_NOTIFICATION_SUBJECT    | Optional    | DTS statuses were changed       | Subject of the message. If not specified, the default theme will be used |

If you want to change the body of the message, you need to put the template.html file in the same folder where the data.json file will be located.