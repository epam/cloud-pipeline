# [MANUAL] [Negative] Try to mark for delete unexisting file

Test verifies that marking a nonexistent file for deletion reports the expected error.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Delete a nonexistent file from the bucket: `pipe storage rm cp://{bucket_name}/{unexisting_file_name}` | An error message like `Storage path "cp://{bucket_name}/{unexisting_file_name}" was not found` is returned |
