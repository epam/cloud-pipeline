# Deactivate Download File Option

Test verifies that Generate URL option for datastorage's files can be disabled for general users.

**Prerequisites**:
- Admin user
- Non-admin user

**Preparations**
1. Login as admin user from the prerequisites
2. Create the object storage
3. Open the created object storage
4. Give all permissions to the non-admin user from the prerequisites
5. Create a file in the storage
6. Save the state of the **Enabled** checkbox and of the eye icon of the `storage.allow.signed.urls` preference (the **Settings** page, the **Preferences** tab)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Open the **Preferences** tab | |
| 4 | Input `storage.allow.signed.urls` into the **Search preferences** field | The preference `storage.allow.signed.urls` is shown |
| 5 | Uncheck the **Enabled** checkbox of the preference from step 4 (if needed) | |
| 6 | Check the eye icon near the preference (if needed) | The eye icon is checked: the preference is visible to users. A non-admin user does not receive a hidden preference |
| 7 | Save changes | |
| 8 | Logout | |
| 9 | Login as non-admin user from the prerequisites | |
| 10 | Open the object storage created at step 2 of the preparations | |
| 11 | Tick the checkbox of the file created at step 5 of the preparations | <li> The **Clear selection** button appears <li> ***Generate URL*** button doesn't appear |

**After**:
- Login as admin user from the prerequisites, and restore the **Enabled** checkbox and the eye icon of the `storage.allow.signed.urls` preference saved at step 6 of the preparations
- Remove the storage created at step 2 of the preparations
