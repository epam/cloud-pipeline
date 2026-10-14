# [MANUAL] RECENTLY COMPLETED RUNS widget

Test verifies the RECENTLY COMPLETED RUNS widget's records and rerun controls for a stopped pipeline and a stopped tool run.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the checkbox near **Recently completed runs** and unset the others |  |
| 4 | Click **OK** | The **RECENTLY COMPLETED RUNS** widget is displayed with at least two records: <li>for the pipeline launched in the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case, containing: a "Stopped" icon in front of the pipeline name; the pipeline name specified at preparation step 4 of the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case; the pipeline version saved at preparation step 6 of the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case; labels: the run ID saved at preparation step 14 of the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case, "on-demand", the node type and disk size saved at preparation step 7 of the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case, "{some time} ago", "estimated price: {price value}" <li>for the tool launched in the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case, containing: a "Stopped" icon in front of the tool name; the tool name selected at step 3 of the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case; labels: the run ID saved at step 9 of the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case, "on-demand", the node type and disk size saved at step 5 of the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case, "{some time} ago", "estimated price: {price value}" |
| 5 | Hover over the record that contains the pipeline name specified at preparation step 4 of the [EPMCMBIBPC-2706](EPMCMBIBPC-2706.md) case | The **RERUN** button appears |
| 6 | Hover over the record that contains the tool name selected at step 3 of the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case | The **RERUN** button appears |
| 7 | Repeat step 6, and click **RERUN** | The **Launch** page of the tool launched in the [EPMCMBIBPC-2707](EPMCMBIBPC-2707.md) case appears |
| 8 | Repeat step 1 |  |
| 9 | Click the completed runs link near the **Explore all** label | The **Runs** page opens with the **COMPLETED RUNS** tab active |
