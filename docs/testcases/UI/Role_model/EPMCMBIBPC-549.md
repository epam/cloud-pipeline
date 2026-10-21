# Check folder permissions for read-only user

Test verifies the folder and its pipelines' UI for a user granted read-only permissions on the folder.

**Prerequisites**:
- At least two users
- At least one user with the ROLE_ADMIN role
- An existing folder with two or more pipelines in it (the pipelines don't need special permissions set for users)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Click on the folder from the prerequisites |  |
| 4 | Click the edit icon in the upper-right corner |  |
| 5 | In the list that appears, click **Edit folder** |  |
| 6 | In the pop-up that appears, click the **Permissions** tab |  |
| 7 | Click the **Add user** icon |  |
| 8 | In the pop-up that appears, enter an existing user name |  |
| 9 | Click **OK** |  |
| 10 | In the list that appears, click on the user name specified at step 8 |  |
| 11 | Set the checkbox in the **Allow** column opposite the **Read** permission |  |
| 12 | Close the pop-up that appeared at step 6 |  |
| 13 | Log out |  |
| 14 | Login as the user specified at step 8 |  |
| 15 | Repeat steps 2-4 |  |
| 16 | In the list that appears, click **Permissions** | <li>A pop-up appears that contains the **Info** (active) and **Permissions** tabs <li>The **Name** field with the folder name is non-editable |
| 17 | In the pop-up that appears, click the **Permissions** tab | The **Permissions** tab with the owner name appears |
| 18 | Close the pop-up |  |
| 19 | Click on any pipeline in the folder |  |
| 20 | Click on the pipeline version | On the **DOCUMENTS** tab, the **Delete**, **Rename**, **Upload**, **RUN** buttons are not displayed |
| 21 | Click the **CODE** tab | On the **CODE** tab, the **Delete**, **Rename**, **Upload**, **+ NEW FILE**, **RUN** buttons are not displayed |
| 22 | Click the **STORAGE RULES** tab | On the **STORAGE RULES** tab, the **Delete**, **Add new rule**, **RUN** buttons are not displayed |
