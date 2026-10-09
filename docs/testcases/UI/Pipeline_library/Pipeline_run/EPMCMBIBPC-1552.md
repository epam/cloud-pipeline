# [MANUAL] On-demand estimated price validation of run pipeline

Test verifies that the estimated price shown for an On-demand run is non-zero.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Open its **Configuration** tab |  |
| 3 | Set **Timeout** to `1` |  |
| 4 | Set the **On-demand** price type |  |
| 5 | Save the changes |  |
| 6 | Click **Run** |  |
| 7 | Click **Launch** |  |
| 8 | Wait for the pipeline to finish |  |
| 9 | Go to this pipeline |  |
| 10 | Click **Run** |  |
| 11 | Select the **On-demand** price type | The estimated price does not equal 0 |
