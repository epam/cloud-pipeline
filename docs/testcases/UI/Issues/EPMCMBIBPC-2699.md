# [MANUAL] Delete comment to issue

Test verifies deleting an issue comment, with a cancel-then-confirm flow.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2697](EPMCMBIBPC-2697.md) case |  |
| 2 | Click on the issue created at the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 3 | Click **Delete** near the comment created at step 1 | A pop-up with the message "Are you sure you want to delete comment?" appears |
| 4 | Click **Cancel** in the pop-up | The comment created at step 1 is displayed in the **Issues** panel |
| 5 | Repeat step 3 |  |
| 6 | Click **OK** in the pop-up | The comment created at step 1 is no longer displayed in the **Issues** panel |
