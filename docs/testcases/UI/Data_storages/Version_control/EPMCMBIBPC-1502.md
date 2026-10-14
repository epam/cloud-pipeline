# Deleting bucket after the disable of versioning

Test verifies that a versioned bucket can still be permanently deleted after versioning is disabled.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new object storage** |  |
| 4 | Specify storage path |  |
| 5 | Set the **Enable versioning** checkbox if it's unset |  |
| 6 | Click **Create** button |  |
| 7 | Open the created storage |  |
| 8 | Click **Upload** button |  |
| 9 | Select a file and confirm the upload in the appeared pop-up window |  |
| 10 | Uncheck the **Show files versions** checkbox |  |
| 11 | Click the delete icon opposite the uploaded file name |  |
| 12 | Open library, click on the edit icon opposite the storage name created at step 6 |  |
| 13 | In the appeared pop-up window, uncheck the **Enable versioning** checkbox, click **Save** button |  |
| 14 | Click on the edit icon opposite the storage name created at step 6 |  |
| 15 | Click **Delete** button |  |
| 16 | Click **Delete** button in the appeared pop-up window | The storage is permanently deleted |
