# Check permissions for edit pipeline

Test verifies that a user granted write permission on a pipeline can rename a code file.

**Prerequisites**:
- The user is not in a group that has any permissions on folders and pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case |  |
| 2 | Set the checkbox in the **Allow** column opposite the **WRITE** permission (the **READ** permission checkbox will be checked automatically) |  |
| 3 | Close the pop-up |  |
| 4 | Log out |  |
| 5 | Login as the user specified at step 7 of the [EPMCMBIBPC-537](EPMCMBIBPC-537.md) case |  |
| 6 | Open the **Library** page |  |
| 7 | Click on the pipeline for which **WRITE** permission was set at step 2 |  |
| 8 | Click on the pipeline version |  |
| 9 | Click the **CODE** tab |  |
| 10 | Click the **Rename** button opposite the code file name |  |
| 11 | In the pop-up that appears, specify a new valid file name |  |
| 12 | Click **OK** | The file name becomes equal to the name specified at step 11 |
| 13 | Refresh the page |  |
