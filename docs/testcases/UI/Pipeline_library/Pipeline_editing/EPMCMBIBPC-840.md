# Validation of pipeline settings viewing

Test verifies that closing the repository settings pop-up with the cross button doesn't remove the pipeline.

**Prerequisites**:
- Perform the [EPMCMBIBPC-285](EPMCMBIBPC-285.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the pipeline from the [EPMCMBIBPC-285](EPMCMBIBPC-285.md) case |  |
| 3 | Click on the gear icon in the upper right corner of the page |  |
| 4 | Click on **Edit repository settings** |  |
| 5 | Click on the cross button in the upper right corner of the pop-up window | <li> the pop-up window is closed <li> the pipeline opened at step 2 is not removed |
