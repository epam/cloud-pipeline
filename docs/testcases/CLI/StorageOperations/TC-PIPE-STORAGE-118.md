# Validation of file hard deletion

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Remove the copied file from the storage | `pipe storage rm cp://{storage_name}/{file_name} -d -y` |  |
| 3. List the storage with versions and details displaying | `pipe storage ls cp://{storage_name} -v -l` | The file `{file_name}` isn't displayed |
