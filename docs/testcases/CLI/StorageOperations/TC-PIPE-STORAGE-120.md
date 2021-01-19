# Validation of restore marked for deletion non-empty folder

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Perform [TC-PIPE-STORAGE-119](TC-PIPE-STORAGE-119.md) case |
| 2. Restore the folder removed in [TC-PIPE-STORAGE-119](TC-PIPE-STORAGE-119.md) case | `pipe storage restore cp://{storage_name}/{folder1_name}` |  |
| 3. List the storage | `pipe storage ls cp://{storage_name}` | The folder removed at step 3 of [TC-PIPE-STORAGE-119](TC-PIPE-STORAGE-119.md) case is displayed (`{folder1_name}` |
