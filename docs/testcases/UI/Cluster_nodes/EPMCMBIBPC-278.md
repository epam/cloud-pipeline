# Filtering nodes by lables

Test verifies filtering the cluster nodes table by a pipeline-ID label.

**Prerequisites**:
- At least two working nodes, started at a different time

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-267](EPMCMBIBPC-267.md) case |  |
| 2 | Click on filter-icon near **Labels** column header |  |
| 3 | In the appeared pop-up specify a pipeline ID for which the node isn't terminated or reused yet |  |
| 4 | Click **OK** button | The table refreshes, only one node with the label equal to the pipeline ID specified at step 3 is displayed |
