# [MANUAL] NOTIFICATIONS widget

Test verifies the NOTIFICATIONS widget's display of system-event notifications by severity, and their active/inactive filtering.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the checkbox near **Notifications** and unset the others |  |
| 4 | Click **OK** | The message "There are no system notifications." is displayed in the **NOTIFICATIONS** widget |
| 5 | Open the **Settings** form |  |
| 6 | Click the **System events** tab |  |
| 7 | Click **+ADD** |  |
| 8 | In the pop-up that appears, specify valid values for the notification title and body |  |
| 9 | Select the **Info** severity type |  |
| 10 | Click **CREATE** |  |
| 11 | Repeat steps 7-10, but select the **Warning** severity type |  |
| 12 | Repeat steps 7-10, but select the **Critical** severity type |  |
| 13 | Set the **Active** checkboxes for the notifications created at steps 10, 11, 12 |  |
| 14 | Click **OK** |  |
| 15 | Refresh the page | The **NOTIFICATIONS** widget shows 3 notifications: <li>for the one created at step 10, containing an "info" icon and the title/body specified at step 8 <li>for the one created at step 11, containing a "warning" icon and the title/body specified at step 11 <li>for the one created at step 12, containing a "critical" icon and the title/body specified at step 12 |
| 16 | Repeat steps 5, 6 |  |
| 17 | Unset the **Active** checkboxes for the notifications created at steps 10, 11 |  |
| 18 | Repeat steps 14-15 | In the **NOTIFICATIONS** widget: <li>the notification created at step 12 is displayed <li>the notifications created at steps 10, 11 are not displayed |

**After**:
- Open the **System events** tab in the **Settings** panel and delete the notifications created at steps 10, 11, 12
