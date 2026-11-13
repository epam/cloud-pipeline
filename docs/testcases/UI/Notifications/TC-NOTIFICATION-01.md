# Do not send notifications if a run's status did not change

Test verifies that notification isn't sent if a run's status doesn't change.

**Prerequisites**:
- Admin user, with an email address in its `email` attribute
- Test tool
- The `PIPELINE_RUN_STATUS` notification on the **Email notifications** tab of the **Settings** page: <li> **Enabled** and **Keep owners informed** are ticked <li> **Statuses to inform** is empty (all statuses) or has `STOPPED` <li> **Subject** ends with the default `Job #$templateParameters["id"] is now $templateParameters["status"]`
- A way to see the emails sent to the admin user: its mailbox, or the log of the notifier service, `/opt/notifier/logs/notifier.log` on the host of `cp-notifier`. The notifier logs each email with its subject and all its recipients: `Email from message <id> with subject '<subject>' formed and will be send to: [<to>], cc: [<cc>], bcc: [<bcc>]`, then `Message with id: <id> was successfully send` once the mail server has accepted it

| Steps | Actions | Expected results |
|:---:|------|---|
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Launch a tool with default settings (`run1`) | |
| 5 | Repeat steps 2-4 (`run2`) | |
| 6 | At the **Runs** page, click the run launched step 5 (`run2`) | |
| 7 | Wait until the **SSH** hyperlink appears | `run1` has the **Running** status |
| 8 | Click the **SSH** hyperlink | |
| 9 | In the opened tab enter and perform the command: `pipe stop -y <run1_ID>` | The output is `RunID <run1_ID> of "<...>" stopped` |
| 10 | Wait (up to 10 minutes) until admin user from the Prerequisites receives email with info that `RUN <run1> has been stopped`: its subject ends with `Job #<run1_ID> is now STOPPED` | Email is received. It is sent to the email address of the admin user, the owner of `run1`. The notifier log has the `formed` line of the email, and the `was successfully send` line with the same message id |
| 11 | Repeat step 9 3 times. If the terminal of step 8 has disconnected meanwhile, click the **SSH** hyperlink of `run2` again | The output is `RunID <run1_ID> of "<...>" stopped` each time: `pipe stop` says so for a run that is already stopped as well |
| 12 | Wait for several minutes (at least 3) to make sure no duplicate emails arrived about `RUN <run1> has been stopped` | Extra email isn't received: the notifier log has no other message with the subject ending with `Job #<run1_ID> is now STOPPED` |
| 13 | Open the API method `/pipeline/restapi/run/<run1_ID>` in a new browser tab | <li> `owner` is the admin user, `status` is `STOPPED` <li> `runStatuses` has one `STOPPED` status only |

**After:**
- Stop the run launched at step 5
