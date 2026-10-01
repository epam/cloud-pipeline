# Wait for pipeline finished

Test verifies that a finished pipeline run moves from the **ACTIVE RUNS** tab to the **COMPLETED RUNS** tab with a Success icon.

**Prerequisites**:
- Perform the [EPMCMBIBPC-305](EPMCMBIBPC-305.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Check that the pipeline run is finished (a green V icon appears in the left upper corner) |  |
| 2 | Open **RUNS** (**ACTIVE RUNS** tab) | The pipeline run from the [EPMCMBIBPC-305](EPMCMBIBPC-305.md) case doesn't display on the **ACTIVE RUNS** tab |
| 3 | Select **COMPLETED RUNS** tab | The pipeline run from the [EPMCMBIBPC-305](EPMCMBIBPC-305.md) case, with a **Success** icon, is displayed on the **COMPLETED RUNS** tab |
