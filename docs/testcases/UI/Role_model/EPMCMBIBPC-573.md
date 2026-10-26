# Check tools page by user with execute permissions

Test verifies tool access for a user granted read+execute permissions on a tool group.

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
| 12 | Click the checkbox in the **Allow** column opposite **READ** and **EXECUTE** permissions |  |
| 13 | Log out |  |
| 14 | Login as the user specified at step 9 |  |
| 15 | Open the **Tools** page | The list of visible tools is equal to the tools list that appeared after step 4 |
| 16 | Click on the registry name | There is only one available registry in the list that appears |
| 17 | Click on any tool |  |
| 18 | Click the **SETTINGS** tab |  |
| 19 | Specify valid values in the **EXECUTION ENVIRONMENT** section | The **Run** button is displayed |
