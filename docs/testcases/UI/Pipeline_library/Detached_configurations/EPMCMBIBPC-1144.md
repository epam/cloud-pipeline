# Validation of "Set as default" behavoir for detach configuration

Test verifies that setting a sub-configuration as default makes new sub-configurations use it as their template, and that each sub-configuration's own edits persist.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case |  |
| 2 | Click on the created configuration |  |
| 3 | Click on **+ ADD** button |  |
| 4 | Specify a valid name for a new configuration (2), click **Create** button |  |
| 5 | Click on the tab with the just-created configuration (2) |  |
| 6 | Specify a new value into the **Disk (Gb)** field (e.g. `17`) |  |
| 7 | Click **Save** button |  |
| 8 | Click **Set as default** |  |
| 9 | Click on **+ ADD** button | The configuration name (2) set as default at step 8 is displayed in the **Template** field |
| 10 | Specify a valid name for a new configuration (3), click **Create** button |  |
| 11 | Click on the tab with the just-created configuration (3) | The value changed at step 6 (`17`) is displayed for the **Disk (Gb)** field |
| 12 | Click **Save** button |  |
| 13 | Navigate to the parent folder |  |
| 14 | Return to the detach configuration | The parameters saved at steps 7 and 12 are displayed |
