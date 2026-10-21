# Check of the change code changes a diagram

Test verifies that adding a task and its call directly in the WDL code shows the new task on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **CODE** tab |  |
| 3 | Click on the `*.wdl` file |  |
| 4 | Click **EDIT** button |  |
| 5 | Add into the code: a new task, and a call of that task |  |
| 6 | Click **SAVE** button |  |
| 7 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 8 | Click **GRAPH** tab | On the diagram, a new task added at step 5 appears |
