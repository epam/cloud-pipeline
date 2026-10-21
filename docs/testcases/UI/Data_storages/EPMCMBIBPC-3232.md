# [MANUAL] Storage synchronization from the DynamoDB

*Note: This testcase is available only for the AWS deployment.*

Test verifies that a storage unregistered and left to be resynchronized from the DynamoDB reappears in the library with its permissions restored.

**Prerequisites**:
- Admin user
- Storage name and path from the **Library** folder (e.g. `F1/F2/F3/SYNC_STORAGE_NAME`)
- The 'storage synchronization' timeout, in seconds
- The expected storage permission: role/user and permissions (e.g. `r:a`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Open the **Library** page |  |
| 3 | Navigate to the storage from the prerequisites |  |
| 4 | Click the edit icon next to the storage name |  |
| 5 | Click the **Delete** button |  |
| 6 | Click the **Unregister** button in the pop-up window | The storage is not visible in the given path |
| 7 | Wait the 'storage synchronization' timeout from the prerequisites | The storage has appeared in the given path |
| 8 | Navigate to the storage |  |
| 9 | Click the edit icon next to the storage name |  |
| 10 | Click the **Permissions** tab | The storage permissions match the permissions from the prerequisites |
