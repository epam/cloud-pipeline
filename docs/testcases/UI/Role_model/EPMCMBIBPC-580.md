# Set tool permissions and check it for group

Test verifies that permissions granted to a group on a tool apply equally to every member of that group.

**Prerequisites**:
- At least two users
- At least one user with the ROLE_ADMIN role
- At least one existing pipeline
- At least one group with several users

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select the **Default** registry |  |
| 4 | Select a group |  |
| 5 | Hover over the **Settings** button (gear icon) |  |
| 6 | In the list that appears, select **Group** -> click the **Edit** item |  |
| 7 | Click the **Permissions** tab |  |
| 8 | Click the **Add group** icon |  |
| 9 | In the pop-up that appears, enter an existing group name from the prerequisites |  |
| 10 | Click **OK** |  |
| 11 | In the list on the **Permissions** tab, click the name of the group entered at step 9 |  |
| 12 | Click the checkboxes in the **Allow** column opposite all permissions |  |
| 13 | Log out |  |
| 14 | Login as user1 from the group specified at step 9 |  |
| 15 | Open the **Tools** page |  |
| 16 | Click on any tool |  |
| 17 | Click the **SETTINGS** tab |  |
| 18 | Log out |  |
| 19 | Login as user2 from the group specified at step 9 |  |
| 20 | Open the **Tools** page |  |
| 21 | Click on the tool selected at step 16 |  |
| 22 | Click the **SETTINGS** tab | The permissions for user1 and user2 are equal |
