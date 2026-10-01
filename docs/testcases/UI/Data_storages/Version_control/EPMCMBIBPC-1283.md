# [MANUAL] Validation of hard deletion files with common keys

Test verifies that hard-deleting a file without an extension doesn't remove another file sharing the same name prefix.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Upload two files on the bucket: `pipe storage cp ./file_name cp://BUCKET_NAME/test_file.txt` and `pipe storage cp ./file_name cp://BUCKET_NAME/test_file` | The files are uploaded |
| 2 | Remove the file without extension from the bucket: `pipe storage rm --hard_delete cp://BUCKET_NAME/test_file` | The `test_file` file is removed, but `test_file.txt` still exists |
