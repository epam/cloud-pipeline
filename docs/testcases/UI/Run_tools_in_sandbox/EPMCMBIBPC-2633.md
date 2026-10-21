# Autopause validation [STOP]

Test verifies that with `system.idle.action = STOP`, both idle Spot and On-demand runs are stopped once the idle timeout elapses.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the system **Settings** window |  |
| 3 | Select the **Preferences** tab |  |
| 4 | Click the **System** menu item in the left panel |  |
| 5 | Input `2` into the `system.max.idle.timeout.minutes` field |  |
| 6 | Input `2` into the `system.idle.action.timeout.minutes` field |  |
| 7 | Input `30` into the `system.idle.cpu.threshold` field |  |
| 8 | Input `STOP` into the `system.idle.action` field |  |
| 9 | Click **Save** button, click **OK** button |  |
| 10 | Logout |  |
| 11 | Login as user |  |
| 12 | Open **Tools** page |  |
| 13 | Select a registry, group and tool (e.g. `shell`), open the selected tool page |  |
| 14 | Click the **v** button near the **Run** button in the right upper corner of the page |  |
| 15 | Click **Custom settings** in the appeared list |  |
| 16 | Click **Exec environment** |  |
| 17 | Set valid values into **Node type** and **Disk (Gb)** |  |
| 18 | In the **Advanced** section click the **Price type** dropdown list and select **Spot** (this item could be unavailable on a specific platform, e.g. for Azure) |  |
| 19 | Click **Launch** button |  |
| 20 | Click **Launch** button in the appeared pop-up |  |
| 21 | Repeat steps 12-17 |  |
| 22 | In the **Advanced** section click the **Price type** dropdown list and select **On-demand** |  |
| 23 | Check that the **Auto pause** checkbox below the **Price type** dropdown list is enabled |  |
| 24 | Repeat steps 19-20 | There are 2 runs on the **ACTIVE RUNS** tab, launched at steps 20 and 24 |
| 25 | Wait until both launched runs disappear from the opened **ACTIVE RUNS** tab (~10-12 minutes) | Both runs are stopped |
| 26 | Click the **COMPLETED RUNS** tab | The 2 runs launched at steps 20 and 24 appear in the completed runs table |

**After**:
- Login as admin and restore the system preferences changed at steps 5-8 to their initial values
