# Validation of pipelines pause/resume (check endpoints)

Test verifies that a paused run's endpoint returns 404 while paused and resumes serving the application after resume.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select tool group |  |
| 4 | Select a tool with an endpoint |  |
| 5 | Hover over the **v** button near the **Run** button -> click **Custom settings** |  |
| 6 | On the appeared page expand the **Advanced** section |  |
| 7 | In the **Price type** dropdown list select **On-demand** (this item could be unavailable on a specific platform, e.g. for Azure) |  |
| 8 | Click **Launch** button |  |
| 9 | Click **Launch** button in the appeared pop-up window |  |
| 10 | In the opened **Runs** page click on the just-launched tool |  |
| 11 | Wait until the hyperlink opposite the **Endpoint** label in the header appears |  |
| 12 | Click the **Endpoint** hyperlink | A new tab with a started application from the tool appears |
| 13 | Close the opened tab, switch back to the tool's run log page |  |
| 14 | Save the **Endpoint** link address |  |
| 15 | Click the **PAUSE** hyperlink in the right upper corner | The run status changes, the label **PAUSING** appears |
| 16 | Click the **PAUSE** button in the appeared pop-up window |  |
| 17 | Wait until the **RESUME** hyperlink appears in the right upper corner |  |
| 18 | Open a new tab, paste the value saved at step 14 into the address bar, press **Enter** | A new tab with the error message `404 Not Found` appears |
| 19 | Repeat step 13 |  |
| 20 | On the tool's run log page, click the **RESUME** hyperlink in the right upper corner |  |
| 21 | Click the **RESUME** button in the appeared pop-up window | The run status changes, the label **RESUMING** appears |
| 22 | Wait until the **PAUSE** hyperlink appears in the right upper corner |  |
| 23 | Repeat step 11 |  |
| 24 | Wait 3 minutes |  |
| 25 | Repeat step 12 | A new tab with a started application from the tool appears |
| 26 | Repeat step 13 |  |
| 27 | Stop the tool's run |  |
