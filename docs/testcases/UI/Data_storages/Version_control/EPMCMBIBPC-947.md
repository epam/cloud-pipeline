# [MANUAL] [Negative] Try to restore unexciting file version

Test verifies that restoring a nonexistent file version reports the expected error.

**Prerequisites**:
- A file with several versions exists

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Run `pipe storage restore cp://{bucket_name}/{file_name} -v {unexisting_version}` | The message `Error: Version "{unexisting_version}" doesn't exist.` is displayed |
