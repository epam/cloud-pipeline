# [MANUAL] PIPELINES widget

Test verifies the PIPELINES widget's record and controls for a created pipeline, including run and history.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v**, click **Pipeline** |  |
| 3 | Specify a valid pipeline name, click **CREATE** |  |
| 4 | Open the **Home** page |  |
| 5 | Click **Configure** in the upper-right corner |  |
| 6 | In the pop-up that appears, set the checkbox near **Pipelines** and unset the others |  |
| 7 | Click **OK** | At least 1 record with the pipeline name created at step 3 appears in the **PIPELINES** widget |
| 8 | Hover over the record with the pipeline name specified at step 3 | The **RUN** and **HISTORY** buttons appear |
| 9 | Repeat step 8 and click on the pipeline name | The page of the pipeline created at step 3 opens |
| 10 | Repeat step 4 |  |
| 11 | Repeat step 8 and click **RUN** | The launch page for the pipeline created at step 3 appears |
| 12 | Click **Launch** |  |
| 13 | In the pop-up that appears, click **Launch** |  |
| 14 | On the **Runs** page, click **STOP** next to the just-launched pipeline name (save the run ID) |  |
| 15 | In the pop-up that appears, click **STOP** |  |
| 16 | Repeat step 4 |  |
| 17 | Repeat step 8 and click **HISTORY** | <li>The **HISTORY** tab of the pipeline created at step 3 opens <li>The table shows a record with the run ID saved at step 14 |
