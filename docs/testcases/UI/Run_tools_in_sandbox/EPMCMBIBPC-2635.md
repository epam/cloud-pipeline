# Autopause validation [PAUSE]

Test verifies that with `system.idle.action = PAUSE`, an idle On-demand run with Auto pause enabled gets paused rather than stopped.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-7 of the [EPMCMBIBPC-2633](EPMCMBIBPC-2633.md) case |  |
| 2 | Input `PAUSE` into the `system.idle.action` field |  |
| 3 | Perform steps 9-17 of the [EPMCMBIBPC-2633](EPMCMBIBPC-2633.md) case |  |
| 4 | In the **Advanced** section click the **Price type** dropdown list and select **On-demand** |  |
| 5 | Check that the **Auto pause** checkbox below the **Price type** dropdown list is enabled |  |
| 6 | Click **Launch** button |  |
| 7 | Click **Launch** button in the appeared pop-up |  |
| 8 | Wait about ~10-12 minutes | The run launched at step 7 is paused and has a **RESUME** hyperlink opposite its name |

**After**:
- Resume the paused run and then stop it
- Login as admin and restore the changed system preferences to their initial values
