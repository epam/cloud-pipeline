# [Negative] Try to restore not removed file

**Prerequisites**: the file `{file_name}` without mark "for deletion" in the existing storage

| Action | `pipe` command | Expected result |
|---|---|---|
| Restore the file without mark "for deletion" | `pipe storage restore cp://{storage_name}/{file_name}` | The message `Error: Latest file version is not deleted. Please specify "--version" parameter.` is displayed |
