# Validation of mark for deletion non-empty folder for non-admin user

**Prerequisites**: login as non-admin user with full permissions to the storage `{storage_name}` (storage is created by the admin)

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage to the sub-folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2. Upload the same file to the existing storage to the parent folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{file_name}` |  |
| 3. Mark the parent folder (`{folder1_name}`) for deletion | `pipe storage rm cp://{storage_name}/{folder1_name} -r -y` |  |
| 4. List the storage | `pipe storage ls cp://{storage_name}` | Empty list is displayed |
| 5. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | Empty list is displayed |
