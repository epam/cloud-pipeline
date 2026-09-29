# [MANUAL] Check access to ssh for not owner and non admin user

Test verifies that a second user, who is neither the run's owner nor an admin, cannot use a copied SSH console link to execute commands.

**Prerequisites**:
- Two users with permissions to launch, read, and edit the same pipeline
- The users must not have administrator rights

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Launch the pipeline as user 1 |  |
| 2 | Navigate to the running pipeline's log page |  |
| 3 | Wait for the SSH link to appear |  |
| 4 | Click **SSH** |  |
| 5 | Copy the link to the tab that opens |  |
| 6 | Login as user 2 |  |
| 7 | Navigate to the running pipeline's page | The SSH link is not displayed |
| 8 | Navigate to the copied link | A console opens in which only the cursor is displayed |
| 9 | Try to run `ls -la` |  |
