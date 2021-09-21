# Ls operation for wrong scheme

**Actions**:
1.  Create storage `[pipe storage create --name {storage_name}...]`
2.	Try to list storage with Cloud-specific path (e.g. `s3` for AWS or `gs` for GCP): `[pipe storage ls {Cloud-specific path}://storage_name/]`
3.  Delete storage.

***
**Expected result:**

Only "cp" scheme is supported.  
Command should exit with exit code '1' and appropriate error message