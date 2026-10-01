# Search for tool run

Test verifies searching for a running tool's run by run ID and its content panel/navigation.

**Prerequisites**:
- An existing tool with an endpoint (e.g. `e2e-endpoints`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page, select a registry and tool group |  |
| 2 | Click on the tool from the prerequisites |  |
| 3 | Hover over the **v** button near **Run** -> click **Custom settings** |  |
| 4 | On the page that appears, expand the **Exec environment**, **Advanced** sections |  |
| 5 | Check the specified values of **Node type**, **Disk (Gb)**, **Price type** |  |
| 6 | Click **Launch**; in the pop-up that appears, click **Launch** |  |
| 7 | On the **Runs** page that opens, click on the just-launched tool |  |
| 8 | Wait until the hyperlink next to the **Endpoint** label in the header appears |  |
| 9 | Click the **Endpoint** hyperlink | A new tab with a started application from the tool appears |
| 10 | Close the opened tab, switch back to the tool's run log page |  |
| 11 | Click the home icon in the left menu panel |  |
| 12 | Click the search icon in the left menu panel |  |
| 13 | Click **RUNS** |  |
| 14 | Enter into the search query the run ID of the pipeline run launched at step 6 (the run ID appeared at step 7) |  |
| 15 | Press **Enter** |  |
| 16 | Hover in the list that appears over the found item with the name containing the run ID entered at step 14 and the tool name from the prerequisites | A content panel appears, containing: <li>a header with: the "Running" status of the run; the run ID equal to the one specified at step 14; the tool name from the prerequisites in the form `<tool_name>:<version_name>`; the node type, disk size and price type checked at step 5 <li>the **Found in id** field <li>the **Endpoints** hyperlink <li>the **Owner**, **Scheduled**, **Started**, **Running for**, **Estimated price** fields <li>a task list |
| 17 | Click the **Endpoints** hyperlink on the content panel that appears | A new tab with a started application from the tool appears |
| 18 | Close the opened tab, switch back to the page with the search panel open |  |
| 19 | Click on the found item from step 16 | The **Run logs** page of the pipeline run launched at step 6 (with the run ID specified at step 14) opens |
