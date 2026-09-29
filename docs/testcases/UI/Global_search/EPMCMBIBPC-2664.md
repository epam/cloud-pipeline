# Search for pipeline run over storage path

Test verifies that a run can be found by searching for a storage path that appears in its logs.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2661](EPMCMBIBPC-2661.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-6 of the [EPMCMBIBPC-2662](EPMCMBIBPC-2662.md) case |  |
| 2 | Click the home icon in the left menu panel |  |
| 3 | Click the search icon in the left menu panel |  |
| 4 | Click **RUNS** |  |
| 5 | Enter into the search query the path name specified at step 3 of the EPMCMBIBPC-2660 case |  |
| 6 | Hover in the list that appears over the found item with the run ID of the pipeline run launched at step 1 | A content panel appears, containing: <li>a header with: the "Success" status of the run; the run ID equal to the one of the pipeline run launched at step 1; the pipeline name specified at step 4 of the EPMCMBIBPC-2653 case <li>the **Found in logs** field with the text `<storage_path_name> mounted to ...` |
