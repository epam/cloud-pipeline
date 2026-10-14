# Check pipeline permissions for read-only user

Test verifies that a user granted read on one pipeline sees only that pipeline's path in the library tree.

**Prerequisites**:
- The user must not be a member of a group that has READ permission on the folders and pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case |  |
| 2 | Click on the checkbox in the **Allow** column opposite the **Read** permission |  |
| 3 | Log out |  |
| 4 | Login as the user specified at step 7 of the [EPMCMBIBPC-537](EPMCMBIBPC-537.md) case |  |
| 5 | Open the **Library** page | In the library tree on the left panel, only the path to the pipeline for which **READ** permission was set at step 2 is displayed |
