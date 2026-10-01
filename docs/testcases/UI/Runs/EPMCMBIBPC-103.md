# Platform usage info

Test verifies that the completed runs table shows the expected columns, RERUN/LOG buttons and Cost field for a pipeline run and two tool runs.

**Prerequisites**:
- Non-admin user
- An empty template-pipeline accessible for that user to read/execute
- 2 different tools accessible for that user to read/execute

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open the pipeline from the prerequisites |  |
| 3 | Launch the opened pipeline |  |
| 4 | Save the run ID of the just-launched pipeline |  |
| 5 | Wait until the pipeline run finishes |  |
| 6 | Open the **Tools** page |  |
| 7 | Launch the 1st tool from the prerequisites |  |
| 8 | Save the run ID of the just-launched tool |  |
| 9 | Wait 3 minutes, then stop the launched tool |  |
| 10 | Open the **Tools** page |  |
| 11 | Launch the 2nd tool from the prerequisites |  |
| 12 | Save the run ID of the just-launched tool |  |
| 13 | Wait 3 minutes, then stop the launched tool |  |
| 14 | Click the **Runs** page |  |
| 15 | Click the **COMPLETED RUNS** page | The page with the platform-usage log table appears. The table contains: <li> columns: **Run**, **Parent run**, **Pipeline**, **Docker image**, **Started**, **Completed**, **Elapsed**, **Owner** <li> **RERUN** buttons at least in the rows with the run IDs saved at steps 4, 8, 12 <li> **LOG** buttons in every row <li> a **Cost** field in every row <li> a total row count of 3 or more |
