# Validate WDL scripts for pipeline running

Test verifies creating a pipeline from the WDL template and launching it to completion.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v**, select **Pipeline** item -> click on **WDL** |  |
| 3 | Specify a valid pipeline name in the appeared pop-up window |  |
| 4 | Click **CREATE** button |  |
| 5 | Click on the created pipeline |  |
| 6 | Click **RUN** button |  |
| 7 | Click **Launch** button |  |
| 8 | Click **Launch** button in the appeared pop-up window | <li> the node is being created or reused for the pipeline running <li> the pipeline run completes correctly, all tasks from the main code file are displayed in the log <li> the run completes with **SUCCESS** status |
