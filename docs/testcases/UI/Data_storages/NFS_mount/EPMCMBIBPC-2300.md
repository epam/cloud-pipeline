# [Negative] validate of exception if user set invalid mount-point for NFS mount

Test verifies that creating an NFS mount with a mount point from the reserved black list is rejected.

**Prerequisites**:
- Login as Admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new FS mount** |  |
| 4 | Specify the storage path value as `{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}` |  |
| 5 | Input the value `/` into the **Mount-point** field |  |
| 6 | Click **Create** button | message with text `Could not create nfs datastorage '{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}', mount point '/' is in black list!` appears. The pop-up window stays open, the storage is not created |
| 7 | Repeat steps 5-6 for all values from the list: `/etc`, `/runs`, `/common`, `/bin`, `/opt`, `/var`, `/home`, `/root`, `/sbin`, `/sys`, `/usr`, `/boot`, `/dev`, `/lib`, `/proc`, `/tmp` | message with text `Could not create nfs datastorage '{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}', mount point '{MOUNT_POINT_FROM_LIST}' is in black list!` appears. The pop-up window stays open, the storage is not created |
