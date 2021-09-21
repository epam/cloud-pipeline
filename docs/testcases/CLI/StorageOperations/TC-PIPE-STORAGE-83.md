# [CLI] MV: download folders with the same keys

**Actions**:
1.  Create storage
2.	Create folders on storage: `cp://storage_name/test/` and `cp://storage_name/test_folder/`
3.	Download `test` folder from storage: `pipe storage cp cp://storage_name/test test -r`
4.  Delete storage

***
**Expected result:**

Only `test` folder is downloaded (folder `test` is deleted, folder `test_folder` exists).