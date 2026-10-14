# Validation of NFS storage creation

Test verifies creating an NFS mount and the initial layout of its page.

**Prerequisites**:
- Login as Admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new NFS mount** |  |
| 4 | Specify the storage path value as `{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}` |  |
| 5 | Input a valid value in the **Alias** field |  |
| 6 | Input a valid value in the **Description** field |  |
| 7 | Click **Create** button | An object with the name from step 5 is displayed in the library-tree on the left panel |
| 8 | Open the created storage | The storage page is displayed, containing: <li> the name from step 5 <li> the row with the field **Description:** and the value from step 6 <li> **Select page**, **Create**, **Upload**, **Show attributes**, gear-icon and **Refresh** buttons <li> the row with a view like `{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}` |
