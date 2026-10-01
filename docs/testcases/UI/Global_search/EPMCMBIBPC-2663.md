# Search for pipeline run

Test verifies the content panel and navigation for a successful pipeline run found via search.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2662](EPMCMBIBPC-2662.md) case |  |
| 2 | Click the home icon in the left menu panel |  |
| 3 | Click the search icon in the left menu panel |  |
| 4 | Click **RUNS** |  |
| 5 | Enter into the search query the pipeline name specified at step 4 of the EPMCMBIBPC-2653 case |  |
| 6 | Press **Enter** |  |
| 7 | Hover in the list that appears over the found item with the run ID of the pipeline run launched at step 5 of the EPMCMBIBPC-2662 case | A content panel appears, containing: <li>a header with: the "Success" status of the run; the run ID equal to the one of the pipeline run launched at step 5 of the EPMCMBIBPC-2662 case; the pipeline name specified at step 4 of the EPMCMBIBPC-2653 case; the pipeline version name saved at step 8 of the EPMCMBIBPC-2658 case; the node type and disk size specified at step 6 of the EPMCMBIBPC-2658 case <li>the **Found in pipelineName**, **Found in description**, **Found in logs** fields <li>a label with the user name who launched the pipeline <li>the **Scheduled**, **Started**, **Finished**, **Estimated price** fields <li>a task list |
| 8 | Click on the found item from step 7 | The **Run logs** page of the pipeline run launched at step 5 of the EPMCMBIBPC-2662 case opens |
