# Add task Properties panel validation

Test verifies the Properties panel content when adding a new task on the WDL graph.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-588](EPMCMBIBPC-588.md) case |  |
| 2 | Click **PROPERTIES** button |  |
| 3 | Click **Actions** button -> **Add call** |  |
| 4 | Click on the appeared workflow task | The **Properties** panel for the selected task appears, containing: <li> **Alias** field with the task name <li> **ADD** button in the **Inputs** section <li> **ADD** button in the **Outputs** section <li> a **Runtime** collapsed header with **add docker configuration** / **add compute node configuration** links <li> a field for command input <li> a **Remove** button |
