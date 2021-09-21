# MV operations: copy between local paths

**Actions**:
1.  Create storage `[pipe storage create --name {storage_name}...]`
2.	Try to copy a file inside local machine file system
3.  Delete storage.

***
**Expected result:**

Error message `Transferring files between local paths is not supported.` is returned.