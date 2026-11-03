# [MANUAL] Validation of disabling autopause

Test verifies that unchecking Auto pause on the launch form keeps the run active past the idle autopause threshold.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-4 of the [EPMCMBIBPC-2635](EPMCMBIBPC-2635.md) case |  |
| 2 | Disable the **Auto pause** checkbox below the **Price type** dropdown list |  |
| 3 | Click **Launch** button |  |
| 4 | Click **Launch** button in the appeared pop-up |  |
| 5 | Wait about ~20 minutes | The run launched at step 4 isn't paused and has a **PAUSE** hyperlink opposite its name |

**After**:
- Stop the launched run
- Login as admin and restore the changed system preferences to their initial values
