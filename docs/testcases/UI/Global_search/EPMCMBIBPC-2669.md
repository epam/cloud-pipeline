# Search for completed tool run

Test verifies searching for a stopped tool run, that its Endpoints link now 404s, and opening its run logs from the result.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2668](EPMCMBIBPC-2668.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Runs** page, **ACTIVE RUNS** tab |  |
| 2 | Find the run launched at step 6 of the [EPMCMBIBPC-2668](EPMCMBIBPC-2668.md) case |  |
| 3 | Click **STOP** in the row opposite the found run |  |
| 4 | Click **STOP** in the pop-up that appears |  |
| 5 | Click the home icon in the left menu panel |  |
| 6 | Click the search icon in the left menu panel |  |
| 7 | Click **RUNS** |  |
| 8 | Enter into the search query the run ID of the pipeline run from step 2 |  |
| 9 | Press **Enter** |  |
| 10 | Hover in the list that appears over the found item with the name containing the run ID entered at step 8 and the tool name from the prerequisites of the [EPMCMBIBPC-2668](EPMCMBIBPC-2668.md) case | A content panel appears, containing: <li>a header with: the "Stopped" status of the run; the run ID equal to the one specified at step 8; the tool name from the prerequisites of the [EPMCMBIBPC-2668](EPMCMBIBPC-2668.md) case in the form `<tool_name>:<version_name>` |
| 11 | Click the **Endpoints** hyperlink on the content panel that appears | A new tab with a "404 Not Found" error appears |
| 12 | Close the opened tab, switch back to the page with the search panel open |  |
| 13 | Click on the found item from step 10 | The **Run logs** page of the stopped pipeline run with the run ID specified at step 8 opens |
