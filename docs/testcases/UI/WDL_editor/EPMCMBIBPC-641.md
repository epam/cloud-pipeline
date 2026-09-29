# Task edit validation

Test verifies the Properties panel content for a task that already has a saved input.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-618](EPMCMBIBPC-618.md) case |  |
| 2 | Click **Save** button |  |
| 3 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 4 | Click **CODE** tab |  |
| 5 | Click **GRAPH** tab |  |
| 6 | Click on the task added at step 1 |  |
| 7 | Click **Properties** button | The **Properties** panel for the selected task appears, containing: <li> **Alias** field with the task name added at step 1 <li> **ADD** button in the **Inputs** section <li> the **Inputs** section has the header value **Inputs (1)** <li> **ADD** button in the **Outputs** section <li> **Use another docker image**, **Use another compute node** checkboxes <li> a field for command input <li> a **DELETE <task_name>** button |
