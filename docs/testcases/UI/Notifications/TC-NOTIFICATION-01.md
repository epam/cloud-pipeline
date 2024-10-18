# [Manual] Do not send notifications if a run's status did not change

Test verifies that notification isn't sent if a run's status doesn't change.

**Prerequisites**:

- Admin user

| Steps | Actions | Expected results |
|:---:|------|---|
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Launch a tool with default settings (`run1`) | |
| 5 | Repeat steps 2-4 (`run2`) | |
| 6 | At the **Runs** page, click the run launched step 5 (`run2`) | |
| 7 | Wait until the **SSH** hyperlink appears | |
| 8 | Click the **SSH** hyperlink | |
| 9 | In the opened tab enter and perform the command: `pipe stop -y <run1_ID>` | |
| 10 | Wait until admin user from the Prerequisites receives email with info that `RUN <run1> has been stopped` | Email is received |
| 11 | Repeat step 9 3 times | |
| 12 | Wait for several minutes to make sure no duplicate emails arrived about `RUN <run1> has been stopped` | Extra email isn't received | 

**After:**
- Stop the run launched at step 5
