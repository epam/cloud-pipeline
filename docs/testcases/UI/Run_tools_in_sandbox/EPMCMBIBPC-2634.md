# Autopause validation [PAUSE_OR_STOP]

Test verifies that with `system.idle.action = PAUSE_OR_STOP`, an idle Spot run is stopped while an idle On-demand run is paused instead.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-7 of the [EPMCMBIBPC-2633](EPMCMBIBPC-2633.md) case |  |
| 2 | Input `PAUSE_OR_STOP` into the `system.idle.action` field |  |
| 3 | Perform steps 9-24 of the [EPMCMBIBPC-2633](EPMCMBIBPC-2633.md) case |  |
| 4 | Wait until the Spot run disappears from the **ACTIVE RUNS** tab and the On-demand run is paused (~10-12 minutes) | <li> the run launched with the **Spot** price type is stopped and disappears from the **ACTIVE RUNS** tab <li> the run launched with the **On-demand** price type is paused and has a **RESUME** hyperlink opposite its name |
| 5 | Click the **COMPLETED RUNS** tab | The run launched with the **Spot** price type appears in the completed runs table |

**After**:
- Resume the paused run and then stop it
- Login as admin and restore the changed system preferences to their initial values
