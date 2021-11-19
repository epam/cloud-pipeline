# [Negative] Try to restore the latest version of non-removed file

**Prerequisites**: the file `{file_name}` without mark "for deletion" in the existing storage

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. List the file without mark "for deletion" with versions and details displaying | `pipe storage ls cp://{storage_name}/{file_name} -v -l` |  |
| 2. Restore the latest version of the file from step 1 | `pipe storage restore cp://{storage_name}/{file_name} -v {latest_version}` | The message `Version "{latest_version}" is already the latest version.` is displayed |
