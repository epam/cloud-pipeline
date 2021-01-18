# Validation of non-empty folder hard deletion

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage to the sub-folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2. Upload the same file to the existing storage to the parent folder | `pipe storage cp ./{file_name} cp://{storage_name}/{folder1_name}/{file_name}` |  |
| 3. Remove the parent folder (`{folder1_name}`) from the storage | `pipe storage rm cp://{storage_name}/{folder1_name} -r -d -y` |  |
| 4. List the storage | `pipe storage ls cp://{storage_name}` | The folder removed at step 3 (`{folder1_name}`) isn't displayed |
| 5. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | The folder removed at step 3 (`{folder1_name}`) isn't displayed |
