# Check tools page by user with read-only permissions

Test verifies that a user with only read permission on a tool group sees that group and its tools, and can create a personal group.

**Prerequisites**:
- The user does not have a personal group in the registry

**Preparations**:
1. Login as admin
2. Open the **Tools** page
3. Select the **Default** registry, select a group
4. Hover over the **Settings** button (gear icon); in the list that appears select **Registry** -> click **Edit**
5. Click the **Permissions** tab
6. Click the **Delete** icon opposite the user name from the prerequisites (if that record exists in the permissions table)
7. Click the **Delete** icon opposite the user group name (**ROLE_USER**) (if that record exists in the permissions table)
8. Close the pop-up

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select the **Default** registry, select a group (the same as at preparation step 3) |  |
| 4 | Hover over the **Settings** button (gear icon); in the list that appears select **Group** -> click **Edit** |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click the **Add user** icon |  |
| 7 | In the pop-up that appears, enter an existing user name from the prerequisites |  |
| 8 | Click **OK** |  |
| 9 | In the list on the **Permissions** tab, click the user name entered at step 7 |  |
| 10 | Click the checkbox in the **Allow** column opposite the **READ** permission, close the pop-up |  |
| 11 | Log out |  |
| 12 | Login as the user specified at step 7 |  |
| 13 | Open the **Tools** page |  |
| 14 | Select the **Default** registry, if it isn't already selected |  |
| 15 | Click the tools group name | At least one group is available in the list that appears: the group selected at step 3 |
| 16 | In the list that appears, click on the group from step 3 | The list of visible tools is equal to the tools list that appeared after step 3 |
| 17 | Hover over the **Settings** button (gear icon) |  |
| 18 | In the list that appears, hover over **Group** | The **+ Create personal** item appears |

**After**:
- Return the permissions for the user group that were changed at preparation step 7
