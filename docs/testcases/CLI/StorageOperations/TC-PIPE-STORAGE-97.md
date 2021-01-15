# [CLI] CP: download folder with skip existing option (negative)

**Actions**:
1.  Create storage
2.  Create local file `file_name`
3.  Upload file to storage: `pipe storage cp file_name cp://storage_name/folder/file_name`
4.	Create local file `folder/file_name` (the size of file should be different from uploaded)
5.  Download folder from storage with `--skip-existing` option: `pipe storage cp cp://storage_name/folder/ folder/ -s -f`
6.  Delete storage

***
**Expected result:**

3.	File `file_name` is uploaded successfully
4.	File `file_name` is created successfully
5.  File `file_name` should not be overwritten