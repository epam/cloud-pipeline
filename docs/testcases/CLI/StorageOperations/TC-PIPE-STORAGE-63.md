# [CLI] Download files with similar keys

**Actions**:
1.  Create storage
2.	Upload 2 files on storage: `test_file` and `test_file.txt`
3.	Download file `test_file`: `pipe storage cp cp://storage_name/test_file test_file`
4.  Delete storage

***
**Expected result:**

2.	Files are uploaded successfully
3.	Only on file `test_file` is downloaded