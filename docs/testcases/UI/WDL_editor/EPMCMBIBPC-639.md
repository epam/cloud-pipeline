# Check REVERT button

Test verifies that Revert changes discards an unsaved task added on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **PROPERTIES** button |  |
| 3 | Click **+ Add task** button |  |
| 4 | Click on the appeared workflow task |  |
| 5 | Change the value in the **Alias** field (e.g. `test`) | <li> at the diagram, a new task appears with the name specified at step 5 <li> **Save**, **Revert changes** buttons are enabled |
| 6 | Click **Revert changes** button | <li> at the diagram, the task with the name specified at step 5 disappears <li> **Save**, **Revert changes** buttons are disabled |
