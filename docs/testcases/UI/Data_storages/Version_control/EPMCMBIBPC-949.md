# [MANUAL] [Negative] Try to restore latest version of file

Test verifies that restoring the already-latest file version reports it's already the latest.

**Prerequisites**:
- A file without a delete marker exists

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Run `pipe storage ls cp://{bucket_name}/{file_name} -v -l` |  |
| 2 | Run `pipe storage restore cp://{bucket_name}/{file_name} -v {latest_version}` | The message `Version "{latest_version}" is already the latest version` is displayed |
