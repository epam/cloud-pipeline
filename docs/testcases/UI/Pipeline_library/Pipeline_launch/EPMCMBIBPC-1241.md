# Validation of run pipeline on "on-demand" instance

Test verifies launching a pipeline on an On-demand instance and that the run log reflects the selected price type via `IsSpot: False`.

**Prerequisites**:
- The test could be run only if this functionality is supported by the specific platform

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the library |  |
| 2 | Create the pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline |  |
| 4 | Click the **RUN** button |  |
| 5 | Click the **Advanced** section |  |
| 6 | Click the **Price type** combobox | A dropdown list appears with items: **Spot**, **On-demand** |
| 7 | In the dropdown list select **On-demand** |  |
| 8 | Click the **Launch** button |  |
| 9 | Click the **Launch** button in the appeared pop-up window |  |
| 10 | On the appeared **ACTIVE RUNS** tab, click the row with the just-launched run |  |
| 11 | Click the **> Instance** | The appeared list shows the row `Price type: On-demand` |
| 12 | Click the task **InitializeNode** | The console output contains the text:<br>`- IsSpot: False` |
