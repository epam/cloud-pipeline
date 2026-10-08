# Add input parameters to workflow

Test verifies that adding a workflow-level input parameter creates a matching entry in `config.json`.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click on the `workflow` object |  |
| 3 | Click **PROPERTIES** button |  |
| 4 | Click **Inputs** collapsed header |  |
| 5 | Click **ADD** button |  |
| 6 | At the **Inputs** section: enter a value into the **Name** field, select a **Type** value, enter a value into the **Value** field |  |
| 7 | Click **Save** button |  |
| 8 | In the appeared pop-up, set the **Update configuration** checkbox |  |
| 9 | Check that the **Configuration** combobox shows the value `default` |  |
| 10 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 11 | Click **CODE** tab |  |
| 12 | Click on the `config.json` file | In the `parameters` section, a new parameter is displayed with the name, type and value specified at step 6 |
