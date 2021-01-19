# [Negative] Try to mark for deletion unexisting file

| Action | `pipe` command | Expected result |
|---|---|---|
| Mark for deletion the unexisting file from the existing storage | `pipe storage rm cp://{storage_name}/{unexisting_file_name}` | The message `Storage path "cp://{storage_name}/{unexisiting_file_name}" was not found` is displayed |
