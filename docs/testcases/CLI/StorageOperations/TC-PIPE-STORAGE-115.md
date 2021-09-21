# Validation of restore marked for deletion file

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Mark for deletion the copied file | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 3. List the storage | `pipe storage ls cp://{storage_name}` | Empty list is displayed |
| 4. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | The name of the file removed at step 2 is displayed (`{file_name}`) |
| 5. Perform the restore of the file removed at step 2 | `pipe storage restore cp://{storage_name}/{file_name}` |  |
| 6. List the storage | `pipe storage ls cp://{storage_name}` | The file removed at step 2 is displayed (`{file_name}`) |
