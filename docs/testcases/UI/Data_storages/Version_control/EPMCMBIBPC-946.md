# [MANUAL] [Negative] Try to hard delete unexisting file

Test verifies that hard-deleting a nonexistent file reports the expected error.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Delete a nonexistent file from the bucket: `pipe storage rm cp://{bucket_name}/{unexisting_file_name} -d` | An error message like `Storage path "cp://{bucket_name}/{unexisting_file_name}" was not found` is returned |
