# Check of the change diagram changes a code

Test verifies that saving a task added on the diagram is reflected in the WDL code.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-618](EPMCMBIBPC-618.md) case |  |
| 2 | Click **Save** button |  |
| 3 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 4 | Click **CODE** tab |  |
| 5 | Click on the `*.wdl` file | <li> **EDIT**, **CLOSE** buttons are displayed <li> in the WDL script, a call of a new task with the alias specified at step 2 of the [EPMCMBIBPC-618](EPMCMBIBPC-618.md) case is displayed <li> in the WDL script, a new task is displayed, with the values specified at steps 3-5 of the [EPMCMBIBPC-618](EPMCMBIBPC-618.md) case shown in that task |
