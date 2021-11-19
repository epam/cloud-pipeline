# [Negative] Validation of non-empty folder hard deletion for non-admin and non-owner user

**Prerequisites**: login as non-admin user with full permissions to the storage `{storage_name}` (storage is created by the admin)

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage to the sub-folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2. Upload the same file to the existing storage to the parent folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{file_name}` |  |
| 3. Remove the parent folder (`{folder1_name}`) from the storage | `pipe storage rm cp://{storage_name}/{folder1_name} -r -d -y` | The message `Access denied` is displayed |
