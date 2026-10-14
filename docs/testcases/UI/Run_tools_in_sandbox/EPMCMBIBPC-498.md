# Check tool access to data storage

Test verifies that a running tool mounts only the storages the user has READ/WRITE permission on.

**Prerequisites**:
- There are 2 storages; for the current user, permissions are set: for the first storage (`bucket_1`) - READ and WRITE allowed, for the second storage (`bucket_2`) - READ and WRITE denied

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-494](EPMCMBIBPC-494.md) case |  |
| 2 | Open the run log page of the tool launched at step 1 |  |
| 3 | Wait until the **MountBuckets** task appears in the task list on the left panel of the page |  |
| 4 | Click the **MountBuckets** task | <li> the task log contains records like `{bucket_1} mounted to /cloud-data/{bucket_name}` <li> the task log doesn't contain records like `{bucket_2} mounted to /cloud-data/{bucket_name}` |
