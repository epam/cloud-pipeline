# [MANUAL] Return tool without permission to execute

Test verifies that returning a completed run of a tool the user's execute permission was revoked from is denied.

**Prerequisites**:
- A user has "read" and "execute" rights for at least one filled-in tool

**Preparations**:
1. Login as the user
2. Click the **Tools** button at the navigation panel
3. Navigate to the tool from the prerequisites
4. Click the **Run** button
5. Click **OK** in the pop-up that appears
6. Click the **STOP** button opposite the started tool on the **Active runs** tab
7. Log out
8. Login as the admin
9. Click the **Tools** button at the navigation panel
10. Navigate to the tool from step 3
11. Click the settings button (gear icon in the upper-right corner)
12. Click **Permissions** in the drop-down list that appears
13. Click on the user name in the user list
14. Check the **execute** checkbox in the **Deny** column
15. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user |  |
| 2 | Click the **Runs** button at the navigation panel |  |
| 3 | Open the **COMPLETED RUNS** tab |  |
| 4 | Find the tool from preparation step 3 in the list |  |
| 5 | Click the **RETURN** button opposite the tool name |  |
| 6 | Click the **Launch** button in the upper-right corner |  |
| 7 | Click **OK** in the pop-up that appears | The message "Access is denied" appears |
