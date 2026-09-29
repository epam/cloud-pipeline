# Check tools page by user with edit permissions

Test verifies tool access for a user granted read+write permissions on a tool group.

**Prerequisites**:
- The user, and their group, have no permissions on any Docker registries or their groups

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select the **Default** registry |  |
| 4 | Select a group |  |
| 5 | Hover over the **Settings** button (gear icon) |  |
| 6 | In the list that appears, select **Group** -> click the **Edit** item |  |
| 7 | Click the **Permissions** tab |  |
| 8 | Click the **Add user** icon |  |
| 9 | In the pop-up that appears, enter an existing user name from the prerequisites |  |
| 10 | Click **OK** |  |
| 11 | In the list on the **Permissions** tab, click the name of the user added at step 9 |  |
| 12 | Click the checkbox in the **Allow** column opposite **READ** and **WRITE** permissions |  |
| 13 | Log out |  |
| 14 | Login as the user specified at step 9 |  |
| 15 | Perform the [EPMCMBIBPC-430](../Tools/EPMCMBIBPC-430.md) case | The result is the same as in the EPMCMBIBPC-430 case |
