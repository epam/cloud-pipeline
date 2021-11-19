# Validation of mark for deletion file

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Create storage with enabled versioning | `pipe storage create -v ...` |  |
| 2. Upload some file to the created storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 3. Mark for deletion the copied file | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 4. List the storage | `pipe storage ls cp://{storage_name}` | Empty list is displayed |
| 5. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | The name of the file removed at step 3 is displayed (`{file_name}`) |
