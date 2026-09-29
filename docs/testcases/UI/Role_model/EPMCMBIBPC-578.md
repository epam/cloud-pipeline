# Check pipeline permissions for group

Test verifies that a group added to a pipeline's Permissions tab is displayed in the group list.

**Prerequisites**:
- At least two users
- At least one user with the ROLE_ADMIN role
- At least one existing pipeline
- At least one group with several users

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Click on any existing pipeline (from the prerequisites) |  |
| 4 | Click the **Edit** button (gear icon) in the upper-right corner |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click the **Add group** icon |  |
| 7 | In the pop-up that appears, enter an existing group name (from the prerequisites) |  |
| 8 | Click **OK** | The group name entered at step 7, with a delete icon, is displayed in the list in the pop-up opened at step 4 |
