# Check permissions for execute pipeline

Test verifies that a user granted execute permission on a pipeline can launch it and see its run and node.

**Prerequisites**:
- The user is not in a group that has any permissions on folders and pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case |  |
| 2 | Set the checkbox in the **Allow** column opposite the **EXECUTE** permission (the **READ** permission checkbox will be checked automatically) |  |
| 3 | Close the pop-up |  |
| 4 | Log out |  |
| 5 | Login as the user specified at step 7 of the [EPMCMBIBPC-537](EPMCMBIBPC-537.md) case |  |
| 6 | Open the **Library** page |  |
| 7 | Click on the pipeline for which **EXECUTE** permission was set at step 2 |  |
| 8 | Click the **RUN** button |  |
| 9 | Specify valid values on the Launch page |  |
| 10 | Click **Launch** |  |
| 11 | In the pop-up that appears, click **Launch** | <li>The pipeline is being started <li>The launched pipeline appears on the **ACTIVE RUNS** tab of the **Runs** page |
| 12 | Open the **Cluster state** page | (Sometime later) The node appears containing a label with a run ID equal to the ID of the running pipeline |
