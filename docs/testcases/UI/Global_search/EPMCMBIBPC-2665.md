# Search for pipeline run by ID

Test verifies finding a run by searching for its run ID.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2662](EPMCMBIBPC-2662.md) case |  |
| 2 | Click the home icon in the left menu panel |  |
| 3 | Click the search icon in the left menu panel |  |
| 4 | Enter into the search query the run ID of the pipeline run launched at step 5 of the EPMCMBIBPC-2662 case |  |
| 5 | Press **Enter** |  |
| 6 | Hover in the list that appears over the found item with the pipeline name created at step 4 of the EPMCMBIBPC-2653 case and the run ID specified at step 3 | A content panel appears, containing: <li>a header with: the "Success" status of the run; the run ID equal to the one of the pipeline run launched at step 5 of the EPMCMBIBPC-2662 case; the pipeline name specified at step 4 of the EPMCMBIBPC-2653 case <li>the **Found in id**, **Found in logs** fields |
