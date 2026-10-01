# [MANUAL] Validation of mounting NFS in docker container

*Note: Can be combined with the `EPMCMBIBPC-309` case.*

Test verifies that an NFS storage with a custom mount point is mounted into the tool's container and logged as such.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2274](EPMCMBIBPC-2274.md) |  |
| 2 | Go to the created NFS storage |  |
| 3 | Click on the gear icon |  |
| 4 | Specify a valid value in the **Mount point** field (e.g. `/mnt/{NFS_STORAGE_NAME}`) |  |
| 5 | Launch the tool |  |
| 6 | Go to the run log page |  |
| 7 | Wait until the `MountDataStorages` task appears | The log contains a line like `-->fs-2a5ab373.efs.eu-central-1.amazonaws.com:/{NFS_STORAGE_NAME} mounted to /mnt/{NFS_STORAGE_NAME}` |
