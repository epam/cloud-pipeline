# [MANUAL] Filtering completed runs by pipeline started date

Test verifies filtering the completed runs table by a minimum start date.

**Prerequisites**:
- Pipelines completed with different statuses, on different dates, with different names

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the **Runs** page |  |
| 2 | Make sure the **COMPLETED RUNS** tab is active |  |
| 3 | Click the icon near the **Started** label |  |
| 4 | In the appeared calendar, select a date earlier than the current one |  |
| 5 | Click **OK** at the bottom of the calendar | Only pipelines started on the date selected at step 4 or later are displayed |
