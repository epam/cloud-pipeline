# Validation of docker.extra_multi

Test verifies that a large `cluster.docker.extra_multi` preference triggers a disk-space warning when pausing a run, and that cancelling the pause leaves the run running.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select tool group |  |
| 4 | Select tool |  |
| 5 | Hover over the **v** button near the **Run** button -> click **Custom settings** |  |
| 6 | On the appeared page expand the **Exec environment** section |  |
| 7 | Specify an instance in the **Instance type** field if it's unset |  |
| 8 | Input `15` into the **Disk (Gb)** field |  |
| 9 | Expand the **Advanced** section |  |
| 10 | In the **Price type** dropdown list select **On-demand** |  |
| 11 | Set the **Start idle** checkbox |  |
| 12 | Click **Launch** button |  |
| 13 | Click **Launch** button in the appeared pop-up window |  |
| 14 | Open the system **Settings** window |  |
| 15 | Select the **Preferences** tab |  |
| 16 | Click the **Cluster** menu item in the left panel |  |
| 17 | Input `1000` into the `cluster.docker.extra_multi` field |  |
| 18 | Click **Save** button |  |
| 19 | Click **OK** button |  |
| 20 | Open the **RUNS** page and click on the just-launched tool |  |
| 21 | Wait until the **PAUSE** hyperlink appears in the right upper corner |  |
| 22 | Click the **PAUSE** hyperlink | A pop-up window appears with the warning message:<br>`This operation may fail due to 'Out of disk' error` |
| 23 | Click **CANCEL** button | <li> the label **PAUSING** doesn't display, the **PAUSE** hyperlink appears again <li> the pipeline is not stopped |

**After**:
- Restore the system preferences changed in the prerequisites to their initial values
