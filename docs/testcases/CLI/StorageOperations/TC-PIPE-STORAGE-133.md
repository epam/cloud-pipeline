# [Negative] Try to hard delete unexisting file

| Action | `pipe` command | Expected result |
|---|---|---|
| Remove the unexisting file from the existing storage | `pipe storage rm cp://{storage_name}/{unexisting_file_name} -d` | The message `Storage path "cp://{storage_name}/{unexisiting_file_name}" was not found` is displayed |
