# Check write permissions for bucket

Test verifies the storage UI available to a user granted read+write permissions on the storage.

**Prerequisites**:
- The user, and their group, have no permissions on the storages

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Click on the storage |  |
| 4 | Click the gear icon in the upper-right corner |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click the **Add user** button |  |
| 7 | In the pop-up that appears, enter an existing user name (from the prerequisites) |  |
| 8 | Click **OK** |  |
| 9 | Click on the user name that appears in the list on the **Permissions** tab |  |
| 10 | In the table that appears, set the checkbox in the **Allow** column opposite the **READ** and **WRITE** permissions |  |
| 11 | Log out |  |
| 12 | Login as the user specified at step 7 |  |
| 13 | Open the **Library** page |  |
| 14 | Click on the storage selected at step 3 | <li>The storage content is displayed <li>The **+ Create**, **Upload**, edit storage (gear icon), **Select page**, **Refresh**, **Show attributes** buttons are displayed <li>The **Download** buttons opposite files are displayed <li>The navigation address bar is displayed |
