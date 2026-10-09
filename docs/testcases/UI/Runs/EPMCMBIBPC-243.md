# [MANUAL] Filtering completed runs by pipeline completed date

Test verifies filtering the completed runs table by a maximum completion date.

**Prerequisites**:
- Pipelines completed with different statuses, on different dates, with different names

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the **Runs** page |  |
| 2 | Make sure the **COMPLETED RUNS** tab is active |  |
| 3 | Click the icon near the **Completed** label |  |
| 4 | In the appeared calendar, select a date later than the current one |  |
| 5 | Click **OK** at the bottom of the calendar | Only pipelines that completed on the date selected at step 4 or earlier are displayed |
