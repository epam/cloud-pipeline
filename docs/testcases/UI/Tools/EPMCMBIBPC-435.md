# Run tool with default settings validation

Test verifies launching a tool with the default **Run** button, without opening custom settings.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-429](EPMCMBIBPC-429.md) case |  |
| 2 | Perform the [EPMCMBIBPC-430](EPMCMBIBPC-430.md) case for the same tool used at step 1 |  |
| 3 | Click **Run** button |  |
| 4 | Click **Launch** button | <li> a node is being created or reused <li> the tool is being run on that node |
| 5 | Click the just-launched run on the **ACTIVE RUNS** page |  |
| 6 | Click the **Instance** collapsed header | <li> the **Docker image** field shows the image name as `{registry_address}/{group_name}/{tool_name}:{version_name}` <li> the **Cmd template** field shows the value equal to the **Cmd template** value entered in the [EPMCMBIBPC-430](EPMCMBIBPC-430.md) case <li> the **Node type**, **Disk** fields show the values equal to the **Instance type** and **Disk (Gb)** values entered in the [EPMCMBIBPC-429](EPMCMBIBPC-429.md) case |
