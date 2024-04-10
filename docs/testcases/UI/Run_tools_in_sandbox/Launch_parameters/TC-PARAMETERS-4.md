# Node memory limits

Test verifies that instance is not killed when process is killed by OOM killer.

**Prerequisites**:
- Admin user

| Steps | Actions | Expected results |
|:-----:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | At the tool page, hover over the **Run v** button | |
| 5 | Click the **Custom settings** button in the list | |
| 6 | Expand the **Exec environment** section | |
| 7 | Set *Node type* as `c5.xlarge` | |
| 8 | Launch the run | |
| 9 | At the **Runs** page, click the just-launched run | |
| 10 | Wait until the **SSH** hyperlink appears | |
| 11 | Click the **SSH** hyperlink | |
| 12 | In the opened tab, enter and perform the command: `yum install -y stress` | |
| 13 | In the opened tab, enter and perform the command: `stress -m 1 --vm-bytes 7G --vm-hang 300` | The output contains <li> `stress: FAIL: [<...>] (<...>) failed run completed in <...>s` |
| 14 | Close the tab | |
| 15 | Check current run status | Current run has `Running` status |
| 15 | Click ***OOM Logs*** task | Record `[WARN] Killed process 13821 (stress)` is shown in the log |

After:
- Stop the run launched at step 8
