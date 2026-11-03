# Create existing NFS mount (negative), validation

Test verifies that creating an NFS mount at a storage path that already exists is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat [EPMCMBIBPC-2274](EPMCMBIBPC-2274.md) with the same value of **Storage path** | An error message like `Error: data storage with name: '{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}' or path: '{NFS_MOUNT_PREFIX}/{NFS_STORAGE_NAME}' already exists` appears |
