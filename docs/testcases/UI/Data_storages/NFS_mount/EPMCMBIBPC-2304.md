# [Negative] Validate of handling invalid path of NFS mount

Test verifies that creating an NFS mount with an invalid storage path is rejected with an error.

**Prerequisites**:
- Login as Admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new NFS mount** |  |
| 4 | Specify the storage path value as `{NFS_MOUNT_PREFIX}/` |  |
| 5 | Click **Create** button |  |
| 6 | Specify the storage path value as `{NFS_MOUNT_PREFIX}/.` |  |
| 7 | Click **Create** button | After steps 5 and 7, the error message `Invalid path` appears |
