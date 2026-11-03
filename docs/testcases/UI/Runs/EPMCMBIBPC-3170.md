# [MANUAL] Platform usage (pipelines)

Test verifies that filtering the completed runs table by pipeline name narrows it down to the successful run of that specific pipeline.

**Prerequisites**:
- Perform the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the **Runs** page |  |
| 2 | Click the **COMPLETED RUNS** page |  |
| 3 | Click the filter icon in the **Pipeline** column |  |
| 4 | Specify the pipeline name launched at step 3 of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, select it, click **OK** button to confirm | In the table, a single row remains with: **Status** (before the run ID) = `Success`, run ID equal to the one saved at step 4 of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case, **Owner** = the user from the prerequisites of the [EPMCMBIBPC-103](EPMCMBIBPC-103.md) case |
