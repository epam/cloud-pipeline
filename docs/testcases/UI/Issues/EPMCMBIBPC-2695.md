# [MANUAL] Delete issue

Test verifies deleting an issue, with a cancel-then-confirm flow.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Click on the issue created at step 1 |  |
| 3 | Click the delete icon next to the issue title | A pop-up with the message "Are you sure you want to delete issue?" appears |
| 4 | Click **Cancel** in the pop-up | The issue created at step 1 is displayed in the **Issues** panel |
| 5 | Repeat step 3 |  |
| 6 | Click **OK** in the pop-up | The following are displayed in the **Issues** panel: <li>the folder name equal to the one specified at step 3 of the EPMCMBIBPC-2691 case <li>the **New issue** button <li>the "No issues found" label |
