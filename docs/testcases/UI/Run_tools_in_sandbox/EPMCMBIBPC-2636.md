# Validation of hdd_extra_multi

Test verifies that a paused-and-resumed run's filesystem is autoscaled enough to keep a large file created before the pause.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only
- Login as admin: open the system **Settings** window, select **Preferences** tab, click **Cluster** menu item in the left panel, input `10` into the `cluster.instance.hdd_extra_multi` field, click **Save** button, click **OK** button

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-17 of the [EPMCMBIBPC-2627](EPMCMBIBPC-2627.md) case |  |
| 2 | Run the command: `fallocate -l 15G test.big` |  |
| 3 | Close the ssh tab, switch back to the tool's run log page, wait ~30 seconds |  |
| 4 | Click the **PAUSE** hyperlink in the right upper corner |  |
| 5 | Click the **PAUSE** button in the appeared pop-up window | The run status changes, the label **PAUSING** appears |
| 6 | Wait until the **RESUME** hyperlink appears instead of the **PAUSING** label |  |
| 7 | Click the **PausePipelineRun** task in the left panel |  |
| 8 | Wait until the text `Docker service was successfully stopped` appears in the log | The following messages appear in the log: <li> `Temporary container was successfully committed` <li> `Docker container logs were successfully retrieved.` <li> `Docker service was successfully stopped` |
| 9 | Click the **RESUME** hyperlink | The log of the **FilesystemAutoscaling** task appears, containing the text `Filesystem .* was autoscaled .*` |
| 10 | Click the **RESUME** button in the appeared pop-up window |  |
| 11 | Wait until the **PAUSE** hyperlink appears instead of the **RESUMING** label |  |
| 12 | Click the **SSH** hyperlink | The run status changes, the label **RESUMING** appears |
| 13 | Switch to the ssh tab |  |
| 14 | Run the command: `ls test.big` | The result of the command execution is `test.big` |

**After**:
- Restore the system preferences changed in the prerequisites to their initial values
