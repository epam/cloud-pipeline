# [Negative] Try to restore unexisting file version

**Prerequisites**: the file `{file_name}` with several versions in the existing storage

| Action | `pipe` command | Expected result |
|---|---|---|
| Restore the unexisting version of the file in the storage | `pipe storage restore cp://{storage_name}/{file_name} -v {unexisting_version}` | The message `Error: Version "{unexisting_version}" doesn't exist.` is displayed |
