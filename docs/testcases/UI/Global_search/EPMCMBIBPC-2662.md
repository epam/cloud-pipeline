# Search for pipeline with runs

Test verifies that a pipeline's search result lists all of its runs, in their respective statuses.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2658](EPMCMBIBPC-2658.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 2 | Click on the pipeline created at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 3 | Click **RUN** |  |
| 4 | Click **Launch** on the launch page |  |
| 5 | Click **Launch** in the pop-up that appears |  |
| 6 | Wait until the just-launched pipeline run completes |  |
| 7 | Repeat steps 1-5 |  |
| 8 | Stop the just-launched pipeline run |  |
| 9 | Repeat steps 1-5 |  |
| 10 | Click the search icon in the left menu panel |  |
| 11 | Click **PIPELINES** |  |
| 12 | Enter into the search query the pipeline name specified at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 13 | Press **Enter** |  |
| 14 | Hover in the list that appears over the found item with the name entered at step 12 | A content panel appears, containing: <li>a header with the pipeline name equal to the name entered at step 12 <li>the **Found in name** field <li>the name of the pipeline version saved at step 8 of the [EPMCMBIBPC-2658](EPMCMBIBPC-2658.md) case <li>a table with 3 rows for the pipeline runs: a run with "Queued"/"Initializing"/"Pulling"/"Running" status, with an empty **COMPLETED** field; a run with "Stopped" status, with a non-empty **COMPLETED** field; a run with "Success" status, with a non-empty **COMPLETED** field |
