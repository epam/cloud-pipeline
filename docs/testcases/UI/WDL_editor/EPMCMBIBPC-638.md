# Check of the change code changes a diagram, for scatter

Test verifies that adding a scatter block directly in the WDL code shows the new scatter on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **CODE** tab |  |
| 3 | Click on the `*.wdl` file |  |
| 4 | Click **EDIT** button |  |
| 5 | Add into the `workflow` section code in the following form:<br>`scatter (<scatter_name> in )`<br>`{`<br>`}` |  |
| 6 | Click **SAVE** button |  |
| 7 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 8 | Click **GRAPH** tab | At the diagram, a new scatter object with the name specified at step 5 is displayed |
