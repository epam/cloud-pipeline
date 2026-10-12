# [MANUAL] ACTIVE RUNS widget with tool

Test verifies the ACTIVE RUNS widget's record and controls for a launched tool with an endpoint.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page |  |
| 2 | Select a registry and tool group |  |
| 3 | Click on the tool with an endpoint (e.g. `e2e-endpoints`) |  |
| 4 | Hover over the **v** button next to **Run**, click **Custom settings** in the list that appears |  |
| 5 | On the **Launch** page, expand the **Exec environment**, **Advanced** sections (save the node type and disk size values) |  |
| 6 | Select **On-demand** in the **Price type** drop-down list |  |
| 7 | Click **Launch** |  |
| 8 | In the pop-up that appears, click **Launch** |  |
| 9 | On the **Runs** page, click on the just-launched run (save the run ID) |  |
| 10 | Open the **Home** page | The **ACTIVE RUNS** widget is displayed with at least one record containing: <li>the tool name selected at step 3 <li>labels: the run ID saved at step 9; "on-demand"; the node type and disk size saved at step 5; "{some time} ago"; "estimated price: {price value}" |
| 11 | Hover over the record that contains the tool name selected at step 3 | The **STOP** button appears |
| 12 | Click on the record that contains the tool name selected at step 3 |  |
| 13 | Wait until the **Endpoint** link appears in the upper-left corner |  |
| 14 | Repeat steps 10-11 | The **OPEN**, **PAUSE**, **STOP** buttons appear |
| 15 | Hover over the record that contains the tool name selected at step 3, and click **OPEN** | A new tab with a started application from the tool appears (for e2e-endpoints: the page with the user name who launched the tool appears) |
| 16 | Close the just-opened tab, return to the tab with the Cloud Pipeline **Home** page |  |
| 17 | Hover over the record that contains the tool name selected at step 3, and click **STOP** |  |
| 18 | Click **STOP** in the pop-up that appears | The record with the tool name selected at step 3 is no longer displayed in the **ACTIVE RUNS** widget |
