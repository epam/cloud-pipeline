# [MANUAL] Filtering completed runs by pipeline parent run

Test verifies filtering the completed runs table by parent run number.

**Prerequisites**:
- Pipelines that have a parent pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the **Runs** page |  |
| 2 | Make sure the **COMPLETED RUNS** tab is active |  |
| 3 | Click the icon near the **Parent run** label |  |
| 4 | In the appeared input field, enter an existing parent pipeline number |  |
| 5 | Click **OK** at the bottom of the pop-up | Only pipelines whose parent pipeline number equals the one entered at step 4 are displayed |
