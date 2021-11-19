# [Negative] Validation of file hard deletion for non-admin and non-owner user

**Prerequisites**: login as non-admin user with full permissions to the storage `{storage_name}` (storage is created by the admin)

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Remove the copied file from the storage | `pipe storage rm cp://{storage_name}/{file_name} -d -y` | The message `Access denied` is displayed |
