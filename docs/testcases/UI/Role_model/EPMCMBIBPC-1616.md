# [MANUAL] User own configuration validation

Test verifies the UI available to a user for their own configuration.

**Prerequisites**:
- Same as the prerequisites of the [EPMCMBIBPC-1613](EPMCMBIBPC-1613.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1615](EPMCMBIBPC-1615.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Navigate to the configuration from the prerequisites | <li>The **Run** and **Save** buttons are located opposite the configuration **Name** field <li>The **+ADD** button is located above the configuration form <li>The settings button is located in the upper-right corner <li>The **Add parameter** button is clickable |
| 4 | Open the **Advanced** tab |  |
| 5 | Click the **Pipeline** field | The "Select pipeline" pop-up appears |
| 6 | Click **Cancel** |  |
| 7 | Click the **Docker image** field | The "Select docker image" pop-up appears |
| 8 | Click **Cancel** |  |
| 9 | Click the **Instance type** field | A drop-down list of instances appears |
| 10 | Change the value in the **Disk (Gb)** field |  |
| 11 | Click the **Price type** field | A drop-down list of price types appears |
| 12 | Type `Sleep 100` in the **Cmd template** field |  |
| 13 | Press the **Save** button | All changes are saved |
| 14 | Click the settings button (gear icon in the upper-right corner) |  |
| 15 | Open the **Permissions** tab |  |
| 16 | Click the add-user button | The "Select user" pop-up appears |
| 17 | Click **Cancel** |  |
| 18 | Click the add-group button | The "Select group" pop-up appears |
| 19 | Click **Cancel** |  |
