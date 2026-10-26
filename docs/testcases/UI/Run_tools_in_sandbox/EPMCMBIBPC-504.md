# Stop tool in sandbox by timeout

Test verifies that a run launched with a Timeout stops automatically with FAILURE status once the timeout elapses.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select tool group |  |
| 4 | Select a tool with an endpoint |  |
| 5 | Hover over the **v** button near the **Run** button, click **Custom settings** |  |
| 6 | Specify valid values in the **Exec environment** section |  |
| 7 | Input a natural number into the **Timeout (min)** field |  |
| 8 | Click **Launch** button |  |
| 9 | Click **Launch** button in the appeared pop-up window | <li> the pipeline run finishes after the number of minutes equal to the value entered at step 7 <li> the pipeline run is stopped with **FAILURE** status |
