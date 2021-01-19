# Validation of mark for deletion file for non-admin user

**Prerequisites**: login as non-admin user with full permissions to the storage `{storage_name}` (storage is created by the admin)

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Remove the copied file | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 3. List the storage | `pipe storage ls cp://{storage_name}` | The file removed at step 2 isn't displayed |
| 4. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | The message `Access denied` is displayed |
