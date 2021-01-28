# Validation of hard deletion files with common keys

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Upload the same file to the existing storage but with another name - add extension, e.g. `.txt` | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}.txt` |  |
| 3. Remove the file without extension (`{folder1_name}`) from the storage | `pipe storage rm cp://{storage_name}/{file_name} -d -y` |  |
| 4. List the storage with versions displaying | `pipe storage ls cp://{storage_name} -v` | The file `{file_name}` isn't displayed, the file `{file_name}.txt` is displayed |
