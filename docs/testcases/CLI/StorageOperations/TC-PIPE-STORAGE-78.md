# [CLI] CP: upload files with similar keys

**Actions**:
1.  Create storage
2.	Create local folder `test_folder` with files: `test` and `test.txt`
3.	Upload `test` file to storage: `pipe storage cp test_folder/test cp://storage_name/test_folder/`
4.  Delete storage

***
**Expected result:**

2.	Files are created.
3.	Only `test` file is uploaded.