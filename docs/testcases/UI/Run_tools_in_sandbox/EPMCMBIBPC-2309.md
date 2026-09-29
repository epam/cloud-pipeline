# Validation of pipelines pause/resume (check SSH)

Test verifies that a file created via SSH before pausing a run is still present after resuming, and that the node monitor page's tabs differ while paused vs. running.

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
| 10 | In the opened **RUNS** page click on the just-launched tool |  |
| 11 | Wait until the **SSH** hyperlink appears in the right upper corner, then click it |  |
| 12 | Switch to the ssh tab |  |
| 13 | Run the command: `echo "test" > test.txt` |  |
| 14 | Close the ssh tab, switch back to the tool's run log page |  |
| 15 | Click the **PAUSE** hyperlink in the right upper corner |  |
| 16 | Click the **PAUSE** button in the appeared pop-up window | The run status changes, the label **PAUSING** appears |
| 17 | Wait until the **RESUME** hyperlink appears in the right upper corner |  |
| 18 | Click the **Instance** item |  |
| 19 | Open the hyperlink from the **IP** field in a new tab | A Node monitor page appears: <li> the **General info** tab is missing <li> the **Jobs** tab is missing |
| 20 | On the tool's run log page, click the **RESUME** hyperlink in the right upper corner |  |
| 21 | Click the **RESUME** button in the appeared pop-up window | The run status changes, the label **RESUMING** appears |
| 22 | Wait until the **PAUSE** and **SSH** hyperlinks appear in the right upper corner |  |
| 23 | Click the **SSH** hyperlink |  |
| 24 | Switch to the ssh tab |  |
| 25 | Run the command: `cat test.txt` | The text `test` is output |
| 26 | Close the ssh tab, switch back to the tool's run log page |  |
| 27 | Repeat steps 18-19 | The Node information page appears |
| 28 | Stop the tool's run |  |
