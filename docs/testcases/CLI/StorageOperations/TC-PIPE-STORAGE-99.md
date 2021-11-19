# [CLI] MV: download file with skip existing option (negative)

**Actions**:
1.  Create storage
2.  Create local file `file_name`
3.  Upload file to storage: `pipe storage cp file_name cp://storage_name/folder/file_name`
4.	Create local file `folder/file_name` (the size of the file must be different from uploaded to storage)
5.  Download file from storage with `--skip-existing` option: `pipe storage mv cp://storage_name/file_name folder/file_name -s -f`
6.  Delete storage

***
**Expected result:**

3.	File `file_name` is uploaded successfully
4.	File `file_name` is created successfully
5.  File `file_name` isn't skipped