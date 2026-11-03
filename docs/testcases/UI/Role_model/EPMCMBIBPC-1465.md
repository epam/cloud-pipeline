# [MANUAL] Permissions on completed runs validation

Test verifies that a pipeline, and its runs, hidden from a second user are not shown to that user in the tree or in the completed-runs list.

**Prerequisites**:
- Two non-admin users. One of the users must have permission to create pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as user 1 |  |
| 2 | Create a pipeline |  |
| 3 | Deny user 2 the ability to view the pipeline |  |
| 4 | Launch the pipeline |  |
| 5 | Stop the pipeline before it completes |  |
| 6 | Login as user 2 |  |
| 7 | Navigate to the **Runs** page | <li>The pipeline created by user 1 is not displayed in the tree for user 2 <li>The pipeline launched by user 1 is not displayed in the completed list for user 2 |
