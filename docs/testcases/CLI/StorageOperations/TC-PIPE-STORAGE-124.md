# Validation of hard deletion of marked for delete file

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Create storage with enabled versioning | `pipe storage create -v ...` |  |
| 2. Upload some file to the created storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 3. Mark for deletion the copied file | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 4. Remove the marked for deletion file | `pipe storage rm cp://{storage_name}/{file_name} -y -d` |  |
| 5. List the storage with versions and details displaying | `pipe storage ls cp://{storage_name} -v -l` | The empty list is displayed |
