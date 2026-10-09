# Check the saving of the parameters in configurations

Test verifies that parameters set in two different configurations are each preserved after switching between them and refreshing the page.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v**, click **Pipeline** item |  |
| 3 | Specify a valid pipeline name in the appeared pop-up window |  |
| 4 | Click **CREATE** button |  |
| 5 | Click on the created pipeline |  |
| 6 | Click on the pipeline version |  |
| 7 | Click on **CONFIGURATION** tab |  |
| 8 | Click **+ADD** button |  |
| 9 | Specify a valid configuration name in the appeared pop-up |  |
| 10 | Click **CREATE** button |  |
| 11 | Hover over **v** near the **Add parameter** button -> click **String parameter** |  |
| 12 | Set a valid name and value for the just-added parameter |  |
| 13 | Click **Save** button |  |
| 14 | Select the **default** configuration |  |
| 15 | Repeat steps 11-13 |  |
| 16 | Refresh the page |  |
| 17 | Select the configuration created at step 10 |  |
| 18 | Select the **default** configuration | The variables set at steps 12 and 15 should be visible |
