# [Negative] Validation of restore marked for deletion file for non-admin and non-owner user

**Prerequisites**: login as non-admin user with full permissions to the storage `{storage_name}` (storage is created by the admin)

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Mark for deletion the copied file | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 3. List the storage | `pipe storage ls cp://{storage_name}` | Empty list is displayed |
| 4. Perform the restore of the file removed at step 2 | `pipe storage restore cp://{storage_name}/{file_name}` | The message `Access denied` is displayed |
