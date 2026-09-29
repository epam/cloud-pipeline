# Check of the change diagram changes a code, for scatter

Test verifies that saving a scatter added on the diagram is reflected in the WDL code.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-628](EPMCMBIBPC-628.md) case |  |
| 2 | Click **Save** button |  |
| 3 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 4 | Click **CODE** tab |  |
| 5 | Click on the `*.wdl` file | <li> **EDIT**, **CLOSE** buttons are displayed <li> in the WDL code, a new `scatter` object is displayed in the form:<br>`scatter (<scatter_name> in )` |
