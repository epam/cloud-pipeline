# Validation of pipelines pause/resume from RUNS page

Test verifies pausing and resuming a running tool from the RUNS page, checking the Endpoint/SSH controls disappear while paused.

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
| 10 | Wait until the **PAUSE** hyperlink opposite the run launched at steps 8-9 appears |  |
| 11 | Click the **PAUSE** hyperlink |  |
| 12 | Click the **PAUSE** button in the appeared pop-up | The run status changes, the label **PAUSING** appears |
| 13 | Wait until the **RESUME** hyperlink opposite the run launched at steps 8-9 appears |  |
| 14 | Click on the run name launched at steps 8-9 | The run log page appears; the **Endpoint** label and **SSH** hyperlink don't display |
| 15 | Open the **Runs** page |  |
| 16 | Click the **RESUME** hyperlink opposite the run name launched at steps 8-9 |  |
| 17 | Click the **RESUME** button in the appeared pop-up | The run status changes, the label **RESUMING** appears |
| 18 | Repeat step 10 |  |
| 19 | Repeat step 14 | The run log page appears; the **Endpoint** label and **SSH** hyperlink are displayed |
