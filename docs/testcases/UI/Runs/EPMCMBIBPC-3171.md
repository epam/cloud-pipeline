# [MANUAL] Platform usage (tools)

Test verifies that filtering the completed runs table by Docker image narrows it down to the stopped run of that specific tool.

**Prerequisites**:
- Perform the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the **Runs** page |  |
| 2 | Click the **COMPLETED RUNS** page |  |
| 3 | Click the filter icon in the **Docker image** column |  |
| 4 | Specify the 1st tool name from the prerequisites of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, select it, click **OK** button to confirm | In the table, a row remains with: **Status** (before the run ID) = `Stopped`, run ID equal to the one saved at step 8 of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, **Owner** = the user from the prerequisites of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case |
| 5 | Repeat step 3 |  |
| 6 | Click the **Clear** button |  |
| 7 | Repeat step 3 |  |
| 8 | Specify the 2nd tool name from the prerequisites of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, select it, click **OK** button to confirm | In the table, a row remains with: **Status** (before the run ID) = `Stopped`, run ID equal to the one saved at step 12 of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, **Owner** = the user from the prerequisites of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case |
