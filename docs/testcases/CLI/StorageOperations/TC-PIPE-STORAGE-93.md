# [CLI] MV: upload file with skip existing option

**Actions**:
1.  Create storage
2.  Upload file to storage: `pipe storage cp file_name cp://storage_name/folder/file_name`
3.	Upload file to storage with `--skip-existing` option: `pipe storage mv file_name cp://storage_name/file_name -s -f`
4.  Delete storage

***
**Expected result:**

2.	File `file_name` is uploaded successfully 
3.	File `file_name` is skipped