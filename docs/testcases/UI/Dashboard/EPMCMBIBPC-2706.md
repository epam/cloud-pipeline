# [MANUAL] ACTIVE RUNS widget with pipeline

Test verifies the ACTIVE RUNS widget's record and controls for a launched pipeline, including pause, resume, terminate, links and stop.

**Prerequisites**:
- An existing storage with a folder in it

**Preparations**:
1. Open the **Library** page
2. Click **+ Create v**
3. In the list that appears, select **Pipeline** -> **SHELL**
4. Specify a valid pipeline name, click **CREATE**
5. Click on the just-created pipeline
6. Click **RUN** in the row with the pipeline version (save the version name)
7. On the **Launch** page, expand the **Exec environment**, **Advanced** sections (save the node type and disk size values)
8. Select **On-demand** in the **Price type** drop-down list
9. Specify the following command into **Cmd template**: `sleep infinity`
10. Hover over the **v** button next to **Add parameter** -> click **Output path parameter**
11. Enter a valid parameter name and specify a path to the folder from the prerequisites
12. Click **Launch**
13. In the pop-up that appears, click **Launch**
14. On the **Runs** page, click on the just-launched run (save the run ID)
15. Wait until the **SSH** link appears in the upper-right corner

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the checkbox near **Active runs** and unset the others |  |
| 4 | Click **OK** | The **ACTIVE RUNS** widget is displayed with at least one record containing: <li>the pipeline name specified at preparation step 4 <li>the pipeline version saved at preparation step 6 <li>labels: the run ID saved at preparation step 14; "on-demand"; the node type and disk size saved at preparation step 7; "{some time} ago"; "estimated price: {price value}" |
| 5 | Hover over the record that contains the pipeline name specified at preparation step 4 | The **LINKS**, **PAUSE**, **STOP** buttons appear |
| 6 | Click on the record that contains the pipeline name specified at preparation step 4 | The run logs page opens for the run with ID saved at preparation step 14 |
| 7 | Repeat step 1 |  |
| 8 | Click the **Explore all active runs** link | The **Runs** page opens with the **ACTIVE RUNS** tab active |
| 9 | Repeat step 1 |  |
| 10 | Hover over the record that contains the pipeline name specified at preparation step 4, and click **PAUSE** | A pop-up with the question "Are you sure you want to pause run {run ID}?" appears, where {run ID} is the one saved at preparation step 14 |
| 11 | In the pop-up that appears, click **PAUSE**, then repeat step 5 | <li>The **PAUSE**, **STOP** buttons disappear <li>The **LINKS** button is displayed <li>A "Pausing" icon appears in front of the pipeline name |
| 12 | Repeat step 6 |  |
| 13 | Wait until the **RESUME** link appears in the upper-right corner | The **RESUME**, **TERMINATE**, **LINKS** buttons appear |
| 14 | Repeat step 1 |  |
| 15 | Repeat step 5 |  |
| 16 | Hover over the record that contains the pipeline name specified at preparation step 4, and click **TERMINATE** | A pop-up with the question "Terminate {run ID}?" appears, where {run ID} is the one saved at preparation step 14 |
| 17 | Click **CANCEL** in the pop-up |  |
| 18 | Hover over the record that contains the pipeline name specified at preparation step 4, and click **RESUME** | A pop-up with the question "Are you sure you want to resume run {run ID}?" appears, where {run ID} is the one saved at preparation step 14 |
| 19 | Click **RESUME** in the pop-up, then repeat step 5 | <li>The **RESUME**, **TERMINATE** buttons disappear <li>The **LINKS** button is displayed <li>A "Resuming" icon appears in front of the pipeline name |
| 20 | Repeat step 6 |  |
| 21 | Wait until the **PAUSE** link appears in the upper-right corner |  |
| 22 | Repeat step 1 |  |
| 23 | Repeat step 5 | The **LINKS**, **PAUSE**, **STOP** buttons appear |
| 24 | Hover over the record that contains the pipeline name specified at preparation step 4, then hover over **LINKS** and click on a link that appears | The folder specified at preparation step 11 opens |
| 25 | Repeat step 1 |  |
| 26 | Hover over the record that contains the pipeline name specified at preparation step 4, and click **STOP** | A pop-up with the question "Stop {run ID}?" appears, where {run ID} is the one saved at preparation step 14 |
| 27 | Click **STOP** in the pop-up | The record with the pipeline name specified at preparation step 4 is no longer displayed in the **ACTIVE RUNS** widget |
