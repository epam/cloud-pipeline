# Deactivate Download File Option

Test verifies that Generate URL option for datastorage's files can be disabled for general users.

**Prerequisites**:
- Admin user
- General user

**Preparations**
1. Login as admin user from the prerequisites
2. Create the object storage
3. Open the created object storage
4. Give all permissions to the general user from the prerequisites
5. Create a file in the storage

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Open the **Preferences** tab | |
| 4 | Find the preference `storage.allow.signed.urls` | |
| 5 | Uncheck checkbox for the preference from step 4 (if needed) | |
| 6 | Save changes | |
| 7 | Logout | |
| 8 | Login as general user from the prerequisites | |
| 9 | Open the object storage created at step 2 of the preparations | |
| 10 | Select file  created at step 5 of the preparations | ***Generate URL*** button doesn't appear | |
