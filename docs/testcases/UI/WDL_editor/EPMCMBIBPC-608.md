# Add task pop-up validation

Test verifies the Properties panel content when adding a new task on the WDL graph.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **PROPERTIES** button |  |
| 3 | Click **+ Add task** button |  |
| 4 | Click on the appeared workflow task | The **Properties** panel for the selected task appears, containing: <li> **Alias** field with the task name <li> **ADD** button in the **Inputs** section <li> **ADD** button in the **Outputs** section <li> **Use another docker image**, **Use another compute node** checkboxes <li> a field for command input <li> a **DELETE <task_name>** button |
