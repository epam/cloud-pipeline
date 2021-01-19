# [CLI] CP: upload file with skip existing option (negative)

**Actions**:
1.  Create storage
2.  Upload file to storage: `pipe storage cp file_name cp://storage_name/folder/file_name`
3.	Modify file `file_name` (size should be changed)
4.  Upload file to storage with `--skip-existing` option: `pipe storage cp file_name cp://storage_name/file_name -s -f`
5.  Delete storage

***
**Expected result:**

2.	File `file_name` should be successfully uploaded
3.	Size of file `file_name` is modified successfully
4.  File `file_name` must not be skipped