# [CLI] MV: upload file with skip existing option (negative)

**Actions**:
1.  Create storage
2.  Upload file to storage: `pipe storage cp file_name cp://storage_name/folder/file_name`
3.	Modify file `file_name` (file size should be changed)
4.  Upload folder to storage with `--skip-existing` option: `pipe storage mv file_name cp://storage_name/file_name -s -f`
5.  Delete storage

***
**Expected result:**

2.	File `file_name` is uploaded successfully
3.	File size is midified successfully
4.  File `file_name` should not be skipped