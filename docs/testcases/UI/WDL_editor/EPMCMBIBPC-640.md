# Add task in scatter in code validation

Test verifies that adding a scatter, a task call inside it, and the task definition directly in the WDL code shows both on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **CODE** tab |  |
| 3 | Click on the `*.wdl` file |  |
| 4 | Click **EDIT** button |  |
| 5 | Add into the `workflow` section code in the following form:<br>`scatter (<scatter_name> in )`<br>`{`<br>`}` |  |
| 6 | Add into the scatter code added at step 5 a task call code:<br>`scatter (<scatter_name> in )`<br>`{`<br>`call <task_name>`<br>`}` |  |
| 7 | Add the task code:<br>`task <task_name>`<br>`{`<br>`}` |  |
| 8 | Click **SAVE** button |  |
| 9 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 10 | Click **GRAPH** tab | At the diagram, the scatter object (with the name specified at steps 5, 6) and the task (with the name specified at steps 6, 7) in it are displayed |
