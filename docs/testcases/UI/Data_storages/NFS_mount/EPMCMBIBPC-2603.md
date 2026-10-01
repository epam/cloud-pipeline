# Validation of NFS storage creation with spaces in path

Test verifies creating an NFS mount whose storage path contains spaces.

**Prerequisites**:
- Login as Admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new NFS mount** |  |
| 4 | Specify the **Storage path** value in the form `{NFS_MOUNT_PREFIX}/{PATH WITH SPACES}` |  |
| 5 | Input a valid value in the **Alias** field |  |
| 6 | Input a valid value in the **Description** field |  |
| 7 | Click **Create** button | A new object is displayed in the library-tree on the left panel |
| 8 | Open the created storage | The NFS-mount page opens, containing: <li> the name from step 5 <li> a label like `Description:` and the value from step 6 <li> **Select page**, **Create**, **Upload**, **Show attributes**, gear-icon, **Refresh** buttons <li> in the breadcrumb, a value like `{NFS_MOUNT_PREFIX}/{PATH WITH SPACES}` |
