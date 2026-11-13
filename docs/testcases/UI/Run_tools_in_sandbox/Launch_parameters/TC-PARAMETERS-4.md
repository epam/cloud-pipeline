# Node memory limits

Test verifies that instance is not killed when process is killed by OOM killer.

**Prerequisites**:
- Admin user
- An instance type with 8 GB of memory is allowed, e.g. `c6i.xlarge`

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | At the tool page, hover over the **Run v** button | |
| 5 | Click the **Custom settings** button in the list | |
| 6 | Expand the **Exec environment** section | |
| 7 | Set *Node type* as the instance type from the prerequisites, e.g. `c6i.xlarge` | |
| 8 | Expand the **Advanced** section. Set *Price type* as `On-demand`, tick the **Do not mount storages** checkbox | |
| 9 | Launch the run | |
| 10 | At the **Runs** page, click the just-launched run | |
| 11 | Wait until the **SSH** hyperlink appears | |
| 12 | Click the **SSH** hyperlink | |
| 13 | In the opened tab, enter and perform the command: `yum install -y stress` | The package is installed |
| 14 | In the opened tab, enter and perform the command: `stress -m 1 --vm-bytes 7G --vm-hang 300` | The output contains <li> `stress: FAIL: [<...>] (<...>) <-- worker <...> got signal 9` <li> `stress: FAIL: [<...>] (<...>) failed run completed in <...>s` |
| 15 | Close the tab | |
| 16 | Check current run status | Current run has `Running` status |
| 17 | Click ***OOM Logs*** task | Record `[WARN] Killed process <process_id> (stress) total-vm:<...>kB, anon-rss:<...>kB, file-rss:<...>kB, shmem-rss:<...>kB` is shown in the log. Newer kernels (e.g. of Amazon Linux 2023) start the record with `Memory cgroup out of memory: ` or `Out of memory: ` after `[WARN] `, and add more fields at its end |

**After:**
- Stop the run launched at step 9
