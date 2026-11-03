# [MANUAL] Filtering completed runs by pipeline name

Test verifies filtering the completed runs table by pipeline name.

**Prerequisites**:
- Pipelines completed with different statuses, on different dates, with different names

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the **Runs** page |  |
| 2 | Make sure the **COMPLETED RUNS** tab is active |  |
| 3 | Click the icon in the table header near the **Pipeline** label |  |
| 4 | Select one of the launched pipelines in the appeared dropdown list |  |
| 5 | Click **OK** at the bottom of the dropdown list | Only the pipeline with the name selected at step 3 is displayed, the other pipelines are not displayed |
